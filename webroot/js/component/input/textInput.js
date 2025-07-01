export function textInput(gridStyle, id, label, required, type, name, placeholder, defaultValue) {
    return `
    <div class="input-group"${gridStyle}>
      <label for="${id}">${label}:${required ? '<span class="required-asterisk">*</span>' : ''}</label>
      <input type="${type}" id="${id}" name="${name}" placeholder="${placeholder}" value="${defaultValue}" ${required ? 'required' : ''}>
    </div>
  `;
}