/**
 * Common tooltip functionality for creating and managing tooltips
 * Used by role badges and info icons
 */

function createTooltip(text) {
  const tooltip = document.createElement('div');
  tooltip.className = 'tooltip';
  tooltip.textContent = text;
  document.body.appendChild(tooltip);
  return tooltip;
}

function positionTooltip(tooltip, targetElement) {
  const rect = targetElement.getBoundingClientRect();
  const tooltipRect = tooltip.getBoundingClientRect();
  const windowWidth = window.innerWidth;

  let top = rect.top - tooltipRect.height - 10;
  let left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);

  // Adjust horizontal position if tooltip would go off-screen
  if (left < 10) {
    left = 10;
  } else if (left + tooltipRect.width > windowWidth - 10) {
    left = windowWidth - tooltipRect.width - 10;
  }

  // Flip to bottom if no room at top
  if (top < 10) {
    top = rect.bottom + 10;
  }

  tooltip.style.top = top + 'px';
  tooltip.style.left = left + 'px';
}

function removeAllTooltips() {
  const tooltips = document.querySelectorAll('.tooltip');
  tooltips.forEach(tooltip => tooltip.remove());
}

function showTooltip(targetElement, text) {
  const tooltip = createTooltip(text);
  positionTooltip(tooltip, targetElement);
  return tooltip;
}

function initializeTooltips(selector, dataAttribute = 'data-tooltip') {
  document.addEventListener('mouseenter', function (e) {
    if (e.target && e.target.classList && e.target.matches(selector)) {
      const tooltipText = e.target.getAttribute(dataAttribute);
      if (tooltipText) {
        showTooltip(e.target, tooltipText);
      }
    }
  }, true);

  document.addEventListener('mouseleave', function (e) {
    if (e.target && e.target.classList && e.target.matches(selector)) {
      removeAllTooltips();
    }
  }, true);
}

export { createTooltip, positionTooltip, removeAllTooltips, showTooltip, initializeTooltips };