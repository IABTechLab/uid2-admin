export function selectInput(options, defaultValue, gridStyle, id, label, required, name) {
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