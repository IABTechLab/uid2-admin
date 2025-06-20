export function operationWithInputs(title, badgeHtml, description, inputsHtml, id) {
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