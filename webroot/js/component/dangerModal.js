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

function showDangerModal(operationCallback, customText) {
    pendingDangerOperation = operationCallback;
    const modal = document.getElementById('dangerModal');
    const confirmationInput = document.getElementById('confirmationInput');
    const confirmButton = document.getElementById('confirmButton');
    
    const modalDescription = modal.querySelector('.modal-description');
    if (modalDescription) {
        const defaultWarning = 'This is a dangerous operation. If used incorrectly it can cause significant damage to the ecosystem.';
        modalDescription.textContent = customText ?? defaultWarning
    }
    
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

export { initializeDangerModal, showDangerModal, hideDangerModal };