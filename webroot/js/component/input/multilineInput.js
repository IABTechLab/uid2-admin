export function multilineInput(id, label, required, name, placeholder, defaultValue) {
    return `
      <div class="input-group" style="grid-column: span 3;">
        <label for="${id}">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
        <textarea id="${id}" name="${name}" placeholder="${placeholder}" rows="3" ${required ? 'required' : ''}>${defaultValue}</textarea>
      </div>
    `;
}