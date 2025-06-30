import {operation} from "./operation.js";

function operationGroup(config) {
    const {type, title, operations = []} = config;
    const operationsHtml = operations.map(operation).join('');

    return `
    <div class="operations-group ${type}">
      <h4>${title}</h4>
      ${operationsHtml}
    </div>
  `;
}

export {operationGroup};