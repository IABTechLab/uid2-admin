import {initializeDangerModal} from './dangerModal.js';
import {generateRoleBadge, initializeRoleBadges} from './roleBadge.js';
import {httpClient} from './httpClient.js';

function createInputField(input) {
  const { id, name, label, type = 'text', placeholder = '', required = false, defaultValue = '', size = 1, options = [] } = input;
  const isMultiLine = size === 'multi-line';
  const gridSpan = isMultiLine ? 3 : (size > 1 ? size : 0);
  const gridStyle = gridSpan > 0 ? ` style="grid-column: span ${gridSpan};"` : '';
  
  if (isMultiLine) {
    return `
      <div class="input-group"${gridStyle}>
        <label for="${id}">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
        <textarea id="${id}" name="${name}" placeholder="${placeholder}" rows="3" ${required ? 'required' : ''}>${defaultValue}</textarea>
      </div>
    `;
  }
  
  if (type === 'checkbox') {
    const isChecked = defaultValue === 'true' || defaultValue === true;
    return `
      <div class="input-group checkbox-input-group"${gridStyle}>
        <label for="${id}">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
        <div class="checkbox-wrapper">
          <input type="checkbox" id="${id}" name="${name}" value="true" ${isChecked ? 'checked' : ''} ${required ? 'required' : ''}>
        </div>
      </div>
    `;
  }
  
  if (type === 'select') {
    const selectOptions = options.map(option => {
      const optionValue = typeof option === 'string' ? option : option.value;
      const optionLabel = typeof option === 'string' ? option : option.label;
      const isSelected = optionValue === defaultValue;
      
      return `<option value="${optionValue}" ${isSelected ? 'selected' : ''}>${optionLabel}</option>`;
    }).join('');
    
    return `
      <div class="input-group"${gridStyle}>
        <label for="${id}">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
        <select id="${id}" name="${name}" ${required ? 'required' : ''}>
          ${selectOptions}
        </select>
      </div>
    `;
  }
  
  return `
    <div class="input-group"${gridStyle}>
      <label for="${id}">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
      <input type="${type}" id="${id}" name="${name}" placeholder="${placeholder}" value="${defaultValue}" ${required ? 'required' : ''}>
    </div>
  `;
}

function createOperation(config) {
  const {
    id,
    title,
    role,
    description = '',
    inputs = []
  } = config;

  const badgeHtml = generateRoleBadge(role);
  const inputsHtml = inputs.length > 0 ? 
    `<div class="accordion-inputs">${inputs.map(input => createInputField(input)).join('')}</div>` : 
    '';

  const hasInputs = inputs.length > 0;
  const hasDescription = description && description.trim() !== '';
  const needsAccordion = hasInputs || hasDescription;
  
  if (!needsAccordion) {
    return `
      <div class="accordion-section no-accordion">
        <div class="accordion-header executable-header" data-execute-id="${id}">
          <div class="accordion-header-left">
            <span>${title}</span>
          </div>
          <div class="accordion-header-right">
            ${badgeHtml}
            <button class="execute-button-inline" id="${id}" title="Execute">▶</button>
          </div>
        </div>
      </div>
    `;
  }
  
  return `
    <div class="accordion-section">
      <div class="accordion-header">
        <div class="accordion-header-left">
          <span>${title}</span>
        </div>
        <div class="accordion-header-right">
          ${badgeHtml}
          <span class="accordion-arrow">▼</span>
        </div>
      </div>
      <div class="accordion-content">
        ${description ? `<p>${description}</p>` : ''}
        ${inputsHtml}
        <div class="execute-container">
          <button class="execute-button" id="${id}">
            ▶ Execute
          </button>
        </div>
      </div>
    </div>
  `;
}

function createOperationsGroup(config) {
  const { type, title, operations = [] } = config;
  const operationsHtml = operations.map(op => createOperation(op)).join('');
  
  return `
    <div class="operations-group ${type}">
      <h4>${title}</h4>
      ${operationsHtml}
    </div>
  `;
}

function initializeOperations(config) {
  let normalizedConfig;
  
  if (Array.isArray(config)) {
    normalizedConfig = config;
  } else {
    normalizedConfig = [];
    
    if (config['read-only'] && config['read-only'].length > 0) {
      normalizedConfig.push({
        type: 'read-only',
        title: 'Read Only Operations',
        operations: config['read-only']
      });
    }
    
    if (config['write'] && config['write'].length > 0) {
      normalizedConfig.push({
        type: 'write',
        title: 'Write Operations',
        operations: config['write']
      });
    }
    
    if (config['danger-zone'] && config['danger-zone'].length > 0) {
      normalizedConfig.push({
        type: 'danger-zone',
        title: '☠️ Danger Zone ☠️',
        operations: config['danger-zone']
      });
    }
  }
  
  const operationsHtml = normalizedConfig.map(group => createOperationsGroup(group)).join('');
  const operationsContainer = document.querySelector('.operations-container');
  if (operationsContainer) {
    operationsContainer.innerHTML = operationsHtml;
  }
  
  if (!document.getElementById('dangerModal')) {
    initializeDangerModal();
  }

  initializeRoleBadges();
  window.operationConfig = flattenOperationConfig(normalizedConfig);
  initializeAccordions();
  initializeInputValidation();
  initializeExecuteHandlers(normalizedConfig);
}

function initializeAccordions() {
  if (!document.body.hasAttribute('data-accordions-initialized')) {
    document.body.setAttribute('data-accordions-initialized', 'true');
    
    document.addEventListener('click', function(e) {
      if (e.target.classList.contains('execute-button') ||
          e.target.classList.contains('execute-button-inline')) {
        return;
      }
      
      if (e.target.closest('.accordion-header')?.classList.contains('executable-header')) {
        return;
      }
      
      const accordionHeader = e.target.closest('.accordion-header');
      if (accordionHeader && !accordionHeader.classList.contains('executable-header')) {
        const content = accordionHeader.nextElementSibling;
        const isActive = accordionHeader.classList.contains('active');
        
        if (isActive) {
          accordionHeader.classList.remove('active');
          content?.classList.remove('active');
        } else {
          accordionHeader.classList.add('active');
          content?.classList.add('active');
        }
      }
    });
  }
}

function flattenOperationConfig(config) {
  const flattened = {};
  config.forEach(group => {
    group.operations.forEach(operation => {
      flattened[operation.id] = operation;
    });
  });
  return flattened;
}

function collectInputValues(operation) {
  const values = {};
  const inputs = operation.inputs || [];
  
  inputs.forEach(input => {
    const element = document.getElementById(input.id);
    if (!element) return;
    
    if (input.type === 'checkbox') {
      values[input.name] = element.checked;
    } else {
      values[input.name] = element.value;
    }
  });
  
  return values;
}

function validateOperation(operation) {
  const inputs = operation.inputs || [];
  const errors = [];
  
  inputs.forEach(input => {
    if (!input.required) return;
    
    const element = document.getElementById(input.id);
    if (!element) return;
    
    let isValid = false;
    let value;
    
    if (input.type === 'checkbox') {
      value = element.checked;
      isValid = value === true;
    } else {
      value = element.value.trim();
      isValid = value.length > 0;
    }
    
    if (!isValid) {
      errors.push({
        input: input,
        element: element,
        message: `${input.label} is required`
      });
    }
  });
  
  return {
    valid: errors.length === 0,
    errors: errors
  };
}

function updateExecuteButtonState(operationId) {
  const operation = window.operationConfig[operationId];
  if (!operation) return;
  
  if (!operation.inputs || operation.inputs.length === 0) {
    return;
  }
  
  const validation = validateOperation(operation);
  const executeButton = document.getElementById(operationId);
  
  if (executeButton) {
    if (validation.valid) {
      executeButton.disabled = false;
      executeButton.style.opacity = '1';
      executeButton.style.cursor = 'pointer';
    } else {
      executeButton.disabled = true;
      executeButton.style.opacity = '0.5';
      executeButton.style.cursor = 'not-allowed';
    }
  }
}

async function executeOperation(operationId) {
  const operation = window.operationConfig[operationId];
  if (!operation) {
    console.error(`Operation ${operationId} not found`);
    return;
  }
  
  try {
    const validation = validateOperation(operation);
    if (!validation.valid) {
      const errorOutput = document.getElementById('errorOutput');
      if (errorOutput) {
        const errorMessages = validation.errors.map(error => error.message).join(', ');
        errorOutput.innerHTML = `Please fill in all required fields: ${errorMessages}`;
        errorOutput.style.display = 'block';
      }
      return;
    }
    
    const errorOutput = document.getElementById('errorOutput');
    const standardOutput = document.getElementById('standardOutput');
    if (errorOutput) {
      errorOutput.innerHTML = '';
      errorOutput.style.display = 'none';
    }
    if (standardOutput) standardOutput.innerHTML = '';
    
    let inputs = collectInputValues(operation);
    
    if (operation.preProcess) {
      inputs = await operation.preProcess(inputs);
      if (!inputs) {
        return;
      }
    }
    
    let apiCall = operation.apiCall;
    if (!apiCall) {
      if (operation.postProcess) {
        await operation.postProcess(null, inputs);
      }
      return;
    }
    
    let url = apiCall.url;
    if (apiCall.getUrl) {
      url = apiCall.getUrl(inputs);
    }
    
    let payload = null;
    if (apiCall.getPayload) {
      payload = apiCall.getPayload(inputs);
    }
    
    const responseData = await httpClient.request(url, apiCall.method, payload);
    
    let finalResult = responseData;
    if (operation.postProcess) {
      finalResult = await operation.postProcess(responseData, inputs);
    }
    
    if (standardOutput) {
      const jsonString = JSON.stringify(finalResult, null, 2);
      standardOutput.innerHTML = syntaxHighlightJson(jsonString);
    }
    
  } catch (error) {
    console.error('Operation failed:', error);
    const errorOutput = document.getElementById('errorOutput');
    const standardOutput = document.getElementById('standardOutput');
    
    const errorMessage = error.message || 'Operation failed';
    
    if (errorOutput) {
      errorOutput.innerHTML = errorMessage;
      errorOutput.style.display = 'block';
    }
    
    if (standardOutput) {
      standardOutput.innerHTML = `<span style="color: #dc3545; font-weight: bold;">Error: ${errorMessage}</span>`;
    }
  }
}

function createDangerModal() {
  return `
    <div id="dangerModal" class="modal">
      <div class="modal-content">
        <h3>☠️ Danger Zone Operation ☠️</h3>
        <p>This is a dangerous operation. If used incorrectly it can cause significant damage to the ecosystem.</p>
        <p>Type <strong>"yes"</strong> if you want to proceed:</p>
        <input type="text" id="confirmationInput" class="confirmation-input" placeholder="Type 'yes' to confirm">
        <div class="modal-buttons">
          <button id="confirmButton" class="confirm-button" disabled>Confirm</button>
          <button id="cancelButton" class="cancel-button">Cancel</button>
        </div>
      </div>
    </div>
  `;
}

function initializeInputValidation() {
  Object.keys(window.operationConfig).forEach(operationId => {
    updateExecuteButtonState(operationId);
  });
  
  document.addEventListener('input', function(e) {
    if (e.target.matches('input, textarea, select')) {
      const operationId = findOperationForInput(e.target);
      if (operationId) {
        updateExecuteButtonState(operationId);
      }
    }
  });
  
  document.addEventListener('change', function(e) {
    if (e.target.matches('input[type="checkbox"], select')) {
      const operationId = findOperationForInput(e.target);
      if (operationId) {
        updateExecuteButtonState(operationId);
      }
    }
  });
}

function findOperationForInput(inputElement) {
  const accordionSection = inputElement.closest('.accordion-section');
  if (accordionSection) {
    const executeButton = accordionSection.querySelector('.execute-button, .execute-button-inline');
    return executeButton ? executeButton.id : null;
  }
  return null;
}

function syntaxHighlightJson(json) {
  if (typeof json !== 'string') {
    json = JSON.stringify(json, null, 2);
  }
  
  json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  
  return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
    let cls = 'json-number';
    if (/^"/.test(match)) {
      if (/:$/.test(match)) {
        cls = 'json-key';
      } else {
        cls = 'json-string';
      }
    } else if (/true|false/.test(match)) {
      cls = 'json-boolean';
    } else if (/null/.test(match)) {
      cls = 'json-null';
    }
    return '<span class="' + cls + '">' + match + '</span>';
  });
}

function initializeExecuteHandlers(config) {
  // Only add event listener if not already added
  if (!document.body.hasAttribute('data-execute-handlers-initialized')) {
    document.body.setAttribute('data-execute-handlers-initialized', 'true');
    
    document.addEventListener('click', function(e) {
      if (e.target.classList.contains('execute-button') || e.target.classList.contains('execute-button-inline')) {
        const operationId = e.target.id;
        
        // Check if this is a danger zone operation
        const isDangerZone = config.some(group => 
          group.type === 'danger-zone' && 
          group.operations.some(op => op.id === operationId)
        );
        
        if (isDangerZone) {
          // Import and show danger modal
          import('./dangerModal.js').then(module => {
            module.showDangerModal(() => executeOperation(operationId));
          });
        } else {
          executeOperation(operationId);
        }
      }
    });
  }
}

export { createInputField, createOperation, createOperationsGroup, initializeOperations, createDangerModal, executeOperation };