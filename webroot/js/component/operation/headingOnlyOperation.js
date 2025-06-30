export function headingOnlyOperation(id, title, badgeHtml) {
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