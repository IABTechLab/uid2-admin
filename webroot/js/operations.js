import {initializeDangerModal} from './dangerModal.js';
import {generateRoleBadge, initializeRoleBadges} from './roleBadge.js';
import {httpClient} from './httpClient.js';

function createInputField(input, operationId) {
  const { name, label, type = 'text', placeholder = '', required = false, defaultValue = '', size = 1, options = [] } = input;
  const id = `${operationId}-${name}`;
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
    `<div class="accordion-inputs">${inputs.map(input => createInputField(input, id)).join('')}</div>` : 
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

function normalizeOperationConfig(config) {
  // Handle new format: { read: [...], write: [...], danger: [...] }
  const normalizedConfig = [];
  const sections = [
    { key: 'read', type: 'read-only', defaultTitle: 'Read Only Operations' },
    { key: 'write', type: 'write', defaultTitle: 'Write Operations' },
    { key: 'danger', type: 'danger-zone', defaultTitle: '☠️ Danger Zone ☠️' }
  ];
  
  sections.forEach(section => {
    const sectionConfig = config[section.key];
    if (sectionConfig) {
      const processedSection = processSection(sectionConfig, section.defaultTitle);
      if (processedSection && processedSection.operations.length > 0) {
        normalizedConfig.push({
          type: section.type,
          title: processedSection.title,
          operations: processedSection.operations
        });
      }
    }
  });
  
  return normalizedConfig;
}

function processSection(sectionConfig, defaultTitle) {
  if (!sectionConfig) return null;
  
  // Handle simple array format
  if (Array.isArray(sectionConfig)) {
    return {
      title: defaultTitle,
      operations: sectionConfig
    };
  }
  
  // Handle object format with custom title
  if (sectionConfig.operations) {
    return {
      title: sectionConfig.title || defaultTitle,
      operations: sectionConfig.operations
    };
  }
  
  // Fallback for invalid format
  return {
    title: defaultTitle,
    operations: sectionConfig
  };
}

function validateOperationConfig(config) {
  if (!config) {
    throw new Error('Operation configuration is required');
  }
  
  // Validate object format structure
  if (typeof config !== 'object' || Array.isArray(config)) {
    throw new Error('Operation configuration must be an object');
  }
  
  // Check if it has valid sections
  const hasValidSections = config.read || config.write || config.danger;
  
  if (!hasValidSections) {
    console.warn('No valid operation sections found. Expected read/write/danger properties.');
  }
  
  // Validate operations in each section
  ['read', 'write', 'danger'].forEach(sectionName => {
    const sectionConfig = config[sectionName];
    if (sectionConfig) {
      if (Array.isArray(sectionConfig)) {
        validateOperations(sectionConfig, sectionName);
      } else if (sectionConfig.operations && Array.isArray(sectionConfig.operations)) {
        validateOperations(sectionConfig.operations, sectionName);
      } else if (typeof sectionConfig === 'object') {
        // Allow object format for custom titles, but warn if no operations
        if (!sectionConfig.operations) {
          console.warn(`Section '${sectionName}' has object format but no operations array`);
        }
      }
    }
  });
}

function validateOperations(operations, sectionName) {
  operations.forEach((operation, index) => {
    if (!operation.id) {
      throw new Error(`Operation at index ${index} in ${sectionName} is missing required 'id' property`);
    }
    if (!operation.title) {
      throw new Error(`Operation '${operation.id}' in ${sectionName} is missing required 'title' property`);
    }
    if (operation.inputs && !Array.isArray(operation.inputs)) {
      throw new Error(`Operation '${operation.id}' in ${sectionName} has invalid 'inputs' property (must be array)`);
    }
  });
}

function initializeOperations(config) {
  try {
    validateOperationConfig(config);
  } catch (error) {
    console.error('Invalid operation configuration:', error.message);
    const operationsContainer = document.querySelector('.operations-container');
    if (operationsContainer) {
      operationsContainer.innerHTML = `
        <div class="operations-group error">
          <h4>Configuration Error</h4>
          <div class="error-message" style="color: #dc3545; padding: 1rem; border: 1px solid #dc3545; border-radius: 4px; background-color: #f8d7da;">
            <strong>Error:</strong> ${error.message}
          </div>
        </div>
      `;
    }
    return;
  }
  
  const normalizedConfig = normalizeOperationConfig(config);
  
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
    const element = document.getElementById(`${operation.id}-${input.name}`);
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
    
    const element = document.getElementById(`${operation.id}-${input.name}`);
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
        <p class="modal-description"></p>
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
      // Sync values across inputs with the same name
      syncSharedInputValues(e.target);
    }
  });
  
  document.addEventListener('change', function(e) {
    if (e.target.matches('input[type="checkbox"], select')) {
      const operationId = findOperationForInput(e.target);
      if (operationId) {
        updateExecuteButtonState(operationId);
      }
      // Sync values across inputs with the same name
      syncSharedInputValues(e.target);
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

function syncSharedInputValues(changedInput) {
  const inputName = changedInput.name;
  if (!inputName) return;
  
  const newValue = changedInput.type === 'checkbox' ? changedInput.checked : changedInput.value;
  
  // Find all inputs with the same name attribute
  const sharedInputs = document.querySelectorAll(`input[name="${inputName}"], textarea[name="${inputName}"], select[name="${inputName}"]`);
  
  sharedInputs.forEach(input => {
    if (input === changedInput) return; // Skip the input that changed
    
    if (input.type === 'checkbox') {
      input.checked = newValue;
    } else {
      input.value = newValue;
    }
    
    // Trigger validation update for the operation this input belongs to
    const operationId = findOperationForInput(input);
    if (operationId) {
      updateExecuteButtonState(operationId);
    }
  });
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
          // Find the operation to get custom confirmation text
          const operation = window.operationConfig[operationId];
          const customText = operation?.confirmationText;
          
          // Import and show danger modal
          import('./dangerModal.js').then(module => {
            module.showDangerModal(() => executeOperation(operationId), customText);
          });
        } else {
          executeOperation(operationId);
        }
      }
    });
  }
}

export { createInputField, createOperation, createOperationsGroup, initializeOperations, createDangerModal, executeOperation };