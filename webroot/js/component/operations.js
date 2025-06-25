import {initializeDangerModal} from './dangerModal.js';
import {initializeRoleBadges} from './roleBadge.js';
import {httpClient} from '../httpClient.js';
import {initializeTooltips} from './tooltip.js';
import {operationGroup} from "./operation/operationGroup.js";
import {clearError, clearOutput, displayError, displayOutput} from "./output.js";

function normalizeOperationConfig(config) {
    const sections = [
        {key: 'read', type: 'read-only', defaultTitle: 'Read Only Operations'},
        {key: 'write', type: 'write', defaultTitle: 'Write Operations'},
        {key: 'danger', type: 'danger-zone', defaultTitle: '☠️ Danger Zone ☠️'}
    ];

    return sections.flatMap(section => {
        const sectionConfig = config[section.key];
        if (sectionConfig) {
            return [{
                type: section.type,
                title: section.defaultTitle,
                operations: sectionConfig
            }];
        }

        return [];
    });
}

function initializeOperations(config) {
    const normalizedConfig = normalizeOperationConfig(config);

    const operationsHtml = normalizedConfig.map(operationGroup).join('');
    const operationsContainer = document.querySelector('.operations-container');
    if (operationsContainer) {
        operationsContainer.innerHTML = operationsHtml;
    }

    initializeDangerModal();
    initializeRoleBadges();
    window.operationConfig = flattenOperationConfig(normalizedConfig);
    initializeAccordions();
    initializeInputValidation();
    initializeExecuteHandlers(normalizedConfig);
    initializeTooltips('.info-icon');
}

function initializeAccordions() {
    if (!document.body.hasAttribute('data-accordions-initialized')) {
        document.body.setAttribute('data-accordions-initialized', 'true');

        document.addEventListener('click', function (e) {
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
        if (input.type === 'multi-select') {
            const checkboxes = document.querySelectorAll(`input[name="${input.name}"]:checked`);
            const selectedValues = Array.from(checkboxes).map(cb => cb.value);
            values[input.name] = selectedValues.join(','); // Join with comma for backend compatibility
        } else {
            const element = document.getElementById(`${operation.id}-${input.name}`);
            if (!element) return;

            if (input.type === 'checkbox') {
                values[input.name] = element.checked;
            } else {
                values[input.name] = element.value;
            }
        }
    });

    return values;
}

function validateOperation(operation) {
    const inputs = operation.inputs || [];
    const errors = [];

    inputs.forEach(input => {
        if (!input.required) return;

        let isValid = false;
        let value;

        if (input.type === 'multi-select') {
            const checkboxes = document.querySelectorAll(`input[name="${input.name}"]:checked`);
            value = Array.from(checkboxes).map(cb => cb.value);
            isValid = value.length > 0;

            if (!isValid) {
                errors.push({
                    input: input,
                    element: null,
                    message: `${input.label} is required - please select at least one option`
                });
            }
        } else {
            const element = document.getElementById(`${operation.id}-${input.name}`);
            if (!element) return;

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

    clearError();
    clearOutput();

    try {
        const validation = validateOperation(operation);
        if (!validation.valid) {
            displayError(validation.errors.map(error => error.message).join(', '));
            return;
        }

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

        displayOutput(finalResult)

    } catch (error) {
        displayError(error);
    }
}

function initializeInputValidation() {
    Object.keys(window.operationConfig).forEach(operationId => {
        updateExecuteButtonState(operationId);
    });

    document.addEventListener('input', function (e) {
        if (e.target.matches('input, textarea, select')) {
            const operationId = findOperationForInput(e.target);
            if (operationId) {
                updateExecuteButtonState(operationId);
            }
            syncSharedInputValues(e.target);
        }
    });

    document.addEventListener('change', function (e) {
        if (e.target.matches('input[type="checkbox"], select')) {
            const operationId = findOperationForInput(e.target);
            if (operationId) {
                updateExecuteButtonState(operationId);
            }
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

    if (changedInput.type === 'checkbox' && changedInput.closest('.multi-select-group')) {
        const sharedCheckboxes = document.querySelectorAll(`input[name="${inputName}"][value="${changedInput.value}"]`);
        sharedCheckboxes.forEach(checkbox => {
            if (checkbox !== changedInput) {
                checkbox.checked = changedInput.checked;
            }
        });
    } else {
        const newValue = changedInput.type === 'checkbox' ? changedInput.checked : changedInput.value;
        const sharedInputs = document.querySelectorAll(`input[name="${inputName}"], textarea[name="${inputName}"], select[name="${inputName}"]`);

        sharedInputs.forEach(input => {
            if (input === changedInput) return; // Skip the input that changed

            if (input.type === 'checkbox') {
                input.checked = newValue;
            } else {
                input.value = newValue;
            }
        });
    }

    Object.keys(window.operationConfig).forEach(operationId => {
        updateExecuteButtonState(operationId);
    });
}

function initializeExecuteHandlers(config) {
    if (!document.body.hasAttribute('data-execute-handlers-initialized')) {
        document.body.setAttribute('data-execute-handlers-initialized', 'true');

        document.addEventListener('click', function (e) {
            if (e.target.classList.contains('execute-button') || e.target.classList.contains('execute-button-inline')) {
                const operationId = e.target.id;

                const isDangerZone = config.some(group =>
                    group.type === 'danger-zone' &&
                    group.operations.some(op => op.id === operationId)
                );

                if (isDangerZone) {
                    const operation = window.operationConfig[operationId];
                    const customText = operation?.confirmationText;

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


export {initializeOperations, executeOperation};