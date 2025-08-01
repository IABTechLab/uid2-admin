export function multiSelectInput(options, id, defaultValue, name, label, required) {
    const checkboxOptions = options.map(option => {
        const optionValue = typeof option === 'string' ? option : option.value;
        const optionLabel = typeof option === 'string' ? option : option.label;
        const optionHint = typeof option === 'object' && option.hint ? option.hint : '';
        const checkboxId = `${id}-${optionValue}`;
        const defaultValues = Array.isArray(defaultValue) ? defaultValue : (defaultValue ? defaultValue.split(',') : []);
        const isChecked = defaultValues.includes(optionValue);

        const infoIcon = optionHint ? `
        <span class="info-icon" data-tooltip="${optionHint}">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14zm0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16z"/>
            <path d="m8.93 6.588-2.29.287-.082.38.45.083c.294.07.352.176.288.469l-.738 3.468c-.194.897.105 1.319.808 1.319.545 0 1.178-.252 1.465-.598l.088-.416c-.2.176-.492.246-.686.246-.275 0-.375-.193-.304-.533L8.93 6.588zM9 4.5a1 1 0 1 1-2 0 1 1 0 0 1 2 0z"/>
          </svg>
        </span>
      ` : '';

        return `
        <div class="form-check">
          <input type="checkbox" id="${checkboxId}" name="${name}" value="${optionValue}" ${isChecked ? 'checked' : ''} class="form-check-input">
          <label for="${checkboxId}" class="form-check-label">
            ${optionLabel}
            ${infoIcon}
          </label>
        </div>
      `;
    }).join('');

    return `
      <div class="input-group multi-select-group"  style="grid-column: span 3;">
        <label class="multi-select-label">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
        <div class="multi-select-options multi-select-grid">
          ${checkboxOptions}
        </div>
      </div>
    `;
}