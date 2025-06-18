import { createDangerModal } from './operations.js';

function initializeDangerModal() {
    document.body.insertAdjacentHTML('beforeend', createDangerModal());

    const confirmationInput = document.getElementById('confirmationInput');
    const confirmButton = document.getElementById('confirmButton');
    const cancelButton = document.getElementById('cancelButton');

    confirmationInput.addEventListener('input', function() {
        const value = this.value;
        confirmButton.disabled = value !== 'yes';
    });

    confirmButton.addEventListener('click', function() {
        if (pendingDangerOperation) {
            pendingDangerOperation();
        }
        hideDangerModal();
    });

    cancelButton.addEventListener('click', function() {
        hideDangerModal();
    });

    window.addEventListener('click', function(e) {
        if (e.target.id === 'dangerModal') {
            hideDangerModal();
        }
    });
}

let pendingDangerOperation = null;

function showDangerModal(operationCallback) {
    pendingDangerOperation = operationCallback;
    const modal = document.getElementById('dangerModal');
    const confirmationInput = document.getElementById('confirmationInput');
    const confirmButton = document.getElementById('confirmButton');
    
    modal.style.display = 'block';
    confirmationInput.value = '';
    confirmationInput.focus();
    confirmButton.disabled = true;
}

function hideDangerModal() {
    const modal = document.getElementById('dangerModal');
    const confirmationInput = document.getElementById('confirmationInput');
    
    modal.style.display = 'none';
    confirmationInput.value = '';
    pendingDangerOperation = null;
}

export { initializeDangerModal, showDangerModal, hideDangerModal };