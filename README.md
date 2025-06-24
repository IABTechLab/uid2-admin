# UID2 Admin

## Running the app locally for development

### Setup

1. Run `docker-compose up`. This will create a Localstack and initialize it with everything under `./src/main/resources/localstack/`.
2. Wait for localstack to start up and initialize. If it's not ready, the app will crash on boot. 
3. Start the application on CLI or IntelliJ Maven configuration via `mvn clean compile exec:java -Dvertx-config-path=conf/local-config.json -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory -Dlogback.configurationFile=conf/logback.xml`
4. Once started, admin service runs on http://localhost:8089/

### Test data

The data in Localstack is ephemeral so any changes you make to it will disappear on container restart. If you want
to make permanent changes to test data setup, please change corresponding files under `./src/main/resources/localstack`
and re-initialize your localstack by running `docker-compose restart`.

### Authentication and Authorization

When running locally, set the `is_auth_disabled` flag to `true` in [local-config.json](./conf/local-config.json). It disables Okta OAuth and users are logged in as *test.user@unifiedid.com*. The user has all the rights available.

If you want to test with Okta OAuth, set the `is_auth_disabled` flag to `false`, and fill in the `okta_client_secret` with the value under "Okta localhost deployment" in 1Password.

## V2 API

The v2 API is based on individual route provider classes. Each class should provide exactly one endpoint and must implement IRouteProvider or IBlockingRouteProvider. 

### IRouteProvider

**Caution:** When implementing an API endpoint, you need to decide whether you should have a blocking or a non-blocking handler. Non-blocking handlers are suitable for most read-only operations, while most write operations should be done on a blocking handler. If you are calling into a service with a `synchronized` block, you **must** use a blocking handler. You can make your handler blocking by implementing the `IBlockingRouteProvider` interface *instead of* the `IRouteProvider` interface.

IRouteProvider requires a `getHandler` method, which should return a valid handler function - see `GetClientSideKeypairsBySite.java`. This method *must* be annotated with the Path, Method, and Roles annotations.

The route handler will automatically be wrapped by the Auth middleware based on the roles specified in the Roles annotation.

## Working on the UI
Per the above setup steps, the UI runs on `http://localhost:8089/`. To see your local UI changes reflected in the browser, you will need to hard reload (`Crtl+Shift+R`) while on the specific web page you have changed.

### Page setup
```html
<html>
<head>
<!--   We use Unicode symbols for icons so need UTF-8 -->
   <meta charset="UTF-8">
   <link rel="stylesheet" href="/css/style.css">
</head>
<body>

<div class="main-content">
<!--   Operations and controls will be added by `initializeOperations` here -->
   <div class="operations-container"></div>
   
<!--   Both success and error output will be added by `initializeOutput` hedre -->
   <div class="output-container"></div>
</div>

<script type="module">
document.addEventListener('DOMContentLoaded', function () {
   import { initializeOperations } from '/js/component/operations.js';
   import { initializeOutput } from '/js/component/output.js';
   
   const operationConfig = {
      read: [ // Read-only operations (don't modify data)
          // See Operations example below
      ],    
      write: [ // Write operations (modify data)
          // ...
      ],   
      danger: [// Dangerous operations (require explicit confirmation modal)
          // ...
      ]   
   }

   initializeOperations(operationConfig);
   initializeOutput();
});
}
</script>
</body>
</html>
```

### Operations
```javascript
const myOperation = {
  id: 'operationId',           // Required: Unique identifier
  title: 'Operation Title',    // Required: Display name
  role: 'maintainer',          // Required: `maintainer`, `elevated` or `superuser`
  inputs: [                    // Optional: Inputs (see below)
      {
          name: 'field1',      // Value of this input will be available as property matching name on `inputs` object below.
          label: 'Field 1',    // Shown on the UI above the field
          type: 'text'         // Optional, text by default
      }
  ],                           
  description: 'text',         // Optional: Description will appear within the accordion
  confirmationText: 'text',    // Optional: Danger zone confirmation text, will replace stock text if used. Only works in danger zone.
  apiCall: {                   // Optional: API configuration
      method: 'POST',
      url: '',                 // Either hardcore URL here or use `getUrl` below. 
      getUrl: (inputs) => {    // Either construct a URL from the inputs or use `url` above.
          return `/api/my/api?field1=${encodeURIComponent(inputs.field1)}`
      },
      getPayload: (inputs) => {  // Optional: Construct a JS object that will be sent as body
          return { field1: inputs.field1 };
      }
  },                 
  preProcess: async (inputs) => inputs,    // Optional: Run before getUrl/getPayload and the API call
  postProcess: async (data, inputs) => data // Optional: Run after apiCall, if you want to adjust the response 
};

```

### Inputs
- Inputs on the same page that share a `name` will have their values synced.

#### text (default)
Basic small text input
```javascript
const myText = {
  name: 'fieldName',
  label: 'Field Label',
  required: true,              // Optional: If required, Execute won't be enable until filled. Default false.
  size: 2,                     // Optional: grid span 1-3, grid has 3 columns. Default 1.
  placeholder: 'Enter text',   // Optional
  defaultValue: 'default'      // Optional 
};
```

#### multi-line
A larger multi-line text input box
```javascript
const myTextArea = {
  name: 'textArea',
  type: 'multi-line',
  label: 'Multi-line Text',
  placeholder: 'Enter text...',
  defaultValue: 'default text'
};
```

#### number
Number-specific input, won't allow non-number text
```javascript
const myNum = {
  name: 'numField',
  type: 'number',
  label: 'Number Field'
};
```

#### date
Date-specific input with date picker
```javascript
const myDate = {
  name: 'dateField',
  type: 'date',
  defaultValue: '2024-01-01'
};
```

#### checkbox
```javascript
const myCheckbox = {
  name: 'boolField',
  type: 'checkbox',
  label: 'Is it true?',
  defaultValue: true
};
```

#### select
Dropdown to select one option from a list
```javascript
const myDropdown = {
  name: 'Dropdown',
  type: 'select',
  label: 'Select Option',
  options: [
    'simple',                  // Can pass as string if option values are same as UI text
    { value: 'val', label: 'Display' }  // Or as Objects if they are different
  ],
  defaultValue: 'val'
};
```

#### multi-select
Multiple checkboxes to allow selecting more than one option

```javascript
const myMultiSelect = {
  name: 'Multi Select',
  type: 'multi-select',
  label: 'Multiple Options',
  required: true,
  options: [
    {
      value: 'OPTION_1',
      label: 'Option One',
      hint: 'Tooltip explanation'  // Optional, appear as info icons next to options
    }
  ],
  defaultValue: ['OPTION_1']     // Array or comma-separated string
}
```
