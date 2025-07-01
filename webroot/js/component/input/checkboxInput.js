export function checkboxInput(defaultValue, gridStyle, id, label, required, name) {
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