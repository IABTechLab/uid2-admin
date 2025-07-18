let siteList;
let resultsElement = document.querySelector(".search-autocomplete-results");
const searchInput = document.querySelector("#site-search");
searchInput.addEventListener("keyup", searchSitesAutocomplete);

window.addEventListener("mouseup", function (event) {
  var dropdown = document.querySelector(".dropdown-menu");
  if (event.target != dropdown && event.target.parentNode != dropdown) {
    dropdown.style.display = "none";
  }
});

/* ***** multi-use error handler *** */
function participantSummaryErrorHandler(err, divContainer) {
  const errorMessage = prettifyJson(err.responseText);
  const element = document.querySelector(divContainer);
  if (element) {
    element.innerHTML = errorMessage;
    element.style.display = "block";
  }
}

function loadAllSitesCallback(result) {
  siteList = JSON.parse(result).map((item) => {
    return { name: item.name, id: item.id, clientTypes: item.clientTypes };
  });
}

function setSearchValue(searchText) {
  document.querySelector("#site-search").value = searchText;
  resultsElement.style.display = "none";
  document.getElementById("doSearch").click();
}

function searchSitesAutocomplete(e) {
  const searchString = searchInput.value;
  const options = {
    threshold: 0.2,
    minMatchCharLength: 2,
    keys: ["id", "name"],
  };
  const fuse = new Fuse(siteList, options);
  const result = fuse.search(searchString).map((site) => {
    return `<a class="dropdown-item" href="#" onclick="setSearchValue('${site.item.name}')">${site.item.name} (${site.item.id})</a>`;
  });

  let resultHtml = "";
  for (let i = 0; i < result.length; i++) {
    resultHtml += result[i];
  }
  resultsElement.innerHTML = resultHtml;
  if (result.length > 0) {
    resultsElement.style.display = "block";
  } else {
    resultsElement.style.display = "none";
  }
}

function rotateKeysetsCallback(result, keyset_id) {
  const resultJson = JSON.parse(result);
  const formatted = prettifyJson(JSON.stringify(resultJson));
  const element = document.getElementById("rotateKeysetsStandardOutput");
  if (element) {
    element.innerHTML += `keyset_id: ${keyset_id} rotated: <br>${formatted}<br>`;
  }
}

function rotateKeysetsErrorHandler(err, keyset_id) {
  const element = document.getElementById("rotateKeysetsErrorOutput");
  if (element) {
    element.innerHTML += `keyset_id: ${keyset_id} rotation failed: <br>${err}<br>`;
  }
}

function loadSiteCallback(result) {
  const resultJson = JSON.parse(result);

  const domainNames =
    resultJson.domain_names.length > 0 ? resultJson.domain_names : "none";
  const formattedDomains = prettifyJson(JSON.stringify(domainNames));
  const domainElement = document.getElementById("domainNamesStandardOutput");
  if (domainElement) domainElement.innerHTML = formattedDomains;

  const appNames =
    resultJson.app_names.length > 0 ? resultJson.app_names : "none";
  const formattedApps = prettifyJson(JSON.stringify(appNames));
  const appElement = document.getElementById("appNamesStandardOutput");
  if (appElement) appElement.innerHTML = formattedApps;

  delete resultJson.domain_names;
  delete resultJson.app_names;
  let formatted = JSON.stringify(resultJson);
  formatted = prettifyJson(formatted);
  const siteElement = document.getElementById("siteStandardOutput");
  if (siteElement) siteElement.innerHTML = formatted;
}

function loadAPIKeysCallback(result) {
  const textToHighlight = '"disabled": true';
  let resultJson = JSON.parse(result);
  resultJson = resultJson.map((item) => {
    const created = new Date(item.created * 1000).toLocaleString(); // Convert Unix timestamp in seconds to milliseconds for Date constructor
    return { ...item, created };
  });
  const formatted = prettifyJson(JSON.stringify(resultJson));
  const highlightedText = formatted.replaceAll(
    textToHighlight,
    '<span style="background-color: orange;">' + textToHighlight + "</span>"
  );
  const element = document.getElementById("participantKeysStandardOutput");
  if (element) element.innerHTML = highlightedText;
}

function loadKeyPairsCallback(result, siteId) {
  let resultJson = JSON.parse(result);
  let filteredResults = resultJson.filter((item) => {
    return item.site_id === siteId;
  });
  const formatted = prettifyJson(JSON.stringify(filteredResults));
  const element = document.getElementById("keyPairsStandardOutput");
  if (element) element.innerHTML = formatted;
}

function loadEncryptionKeysCallback(result, siteId) {
  const resultJson = JSON.parse(result);
  let filteredResults = resultJson.filter((item) => {
    return item.site_id === siteId;
  });
  let expirations = [];
  let notActivated = [];
  filteredResults = filteredResults.map((item) => {
    const created = new Date(item.created).toLocaleString();
    const activates = new Date(item.activates).toLocaleString();
    const expires = new Date(item.expires).toLocaleString();
    if (item.expires < Date.now()) {
      expirations.push(`"expires": "${expires}"`);
    }
    if (item.activates > Date.now()) {
      notActivated.push(`"activates": "${activates}"`);
    }
    return { ...item, created, activates, expires };
  });

  const formatted = prettifyJson(JSON.stringify(filteredResults));
  let highlightedText = formatted;
  expirations.forEach((item) => {
    highlightedText = highlightedText.replaceAll(
      item,
      '<span style="background-color: orange;">' + item + "</span>"
    );
  });
  notActivated.forEach((item) => {
    highlightedText = highlightedText.replaceAll(
      item,
      '<span style="background-color: yellow;">' + item + "</span>"
    );
  });
  const element = document.getElementById("encryptionKeysStandardOutput");
  if (element) element.innerHTML = highlightedText;
}

function loadOperatorKeysCallback(result, siteId) {
  const textToHighlight = '"disabled": true';
  const resultJson = JSON.parse(result);
  let filteredResults = resultJson.filter((item) => {
    return item.site_id === siteId;
  });
  filteredResults = filteredResults.map((item) => {
    const created = new Date(item.created).toLocaleString();
    return { ...item, created };
  });

  const formatted = prettifyJson(JSON.stringify(filteredResults));
  const highlightedText = formatted.replaceAll(
    textToHighlight,
    '<span style="background-color: orange;">' + textToHighlight + "</span>"
  );
  const element = document.getElementById("operatorKeysStandardOutput");
  if (element) element.innerHTML = highlightedText;
}

function loadOptoutWebhooksCallback(result, siteName) {
  const resultJson = JSON.parse(result);
  const filteredResults = resultJson.filter((item) => {
    return item.name === siteName;
  });
  const formatted = prettifyJson(JSON.stringify(filteredResults));
  const element = document.getElementById("webhooksStandardOutput");
  if (element) element.innerHTML = formatted;
}

function loadRelatedKeysetsCallback(result, siteId, clientTypes) {
  const resultJson = JSON.parse(result);
  resultJson.forEach((obj) => {
    // Keysets where allowed_types include any of the clientTypes that belows to the site
    obj.allowed_types = obj.allowed_types.map((item) => {
      return clientTypes.includes(item) ? `<allowed_types_${item}>` : item;
    });

    // Keysets where allowed_sites include the leaked site. As it's an integer object, change it to a placeholder and replace later.
    if (obj.allowed_sites) {
      obj.allowed_sites = obj.allowed_sites.map((item) => {
        return item === siteId ? "<allowed_sites_matched>" : item;
      });
    }
  });
  const formatted = prettifyJson(JSON.stringify(resultJson));
  let highlightedText = formatted;
  // Highlight ketsets where allowed_sites is set to null
  highlightedText = highlightedText.replaceAll(
    `"allowed_sites": null`,
    '<span style="background-color: orange;">' +
      `"allowed_sites": null` +
      "</span>"
  );
  // Highlight keysets where allowed_types include the site client_types
  highlightedText = highlightedText.replaceAll(
    `"<allowed_types_DSP>"`,
    `<span style="background-color: orange;">"DSP"</span>`
  );
  highlightedText = highlightedText.replaceAll(
    `"<allowed_types_ADVERTISER>"`,
    `<span style="background-color: orange;">"ADVERTISER"</span>`
  );
  highlightedText = highlightedText.replaceAll(
    `"<allowed_types_DATA_PROVIDER>"`,
    `<span style="background-color: orange;">"DATA_PROVIDER"</span>`
  );
  highlightedText = highlightedText.replaceAll(
    `"<allowed_types_PUBLISHER>"`,
    `<span style="background-color: orange;">"PUBLISHER"</span>`
  );
  // Highlight keysets where allowed_sites include the leaked site
  highlightedText = highlightedText.replaceAll(
    `"<allowed_sites_matched>"`,
    `<span style="background-color: orange;">${siteId}</span>`
  );
  // Highlight keysets belonging to the leaked site itself
  highlightedText = highlightedText.replaceAll(
    `"site_id": ${siteId}`,
    '<span style="background-color: orange;">' +
      `"site_id": ${siteId}` +
      "</span>"
  );
  const element = document.getElementById("relatedKeysetsStandardOutput");
  if (element) element.innerHTML = highlightedText;
}

document.addEventListener("DOMContentLoaded", () => {
  const sitesUrl = "/api/site/list";
  doApiCallWithCallback("GET", sitesUrl, loadAllSitesCallback, null);

  const searchButton = document.getElementById("doSearch");
  if (searchButton) {
    searchButton.addEventListener("click", () => {
      const errorOutput = document.getElementById("siteSearchErrorOutput");
      if (errorOutput) errorOutput.style.display = "none";

      const siteSearchInput = document.getElementById("site-search");
      const siteSearch = siteSearchInput ? siteSearchInput.value : "";
      let site = null;
      if (Number.isInteger(Number(siteSearch))) {
        const foundSite = siteList.find((item) => {
          return item.id === Number(siteSearch);
        });
        site = foundSite;
      } else {
        const foundSite = siteList.find((item) => {
          return item.name === siteSearch;
        });
        site = foundSite;
      }
      if (!site) {
        if (errorOutput) {
          errorOutput.textContent = `site not found: ${siteSearch}`;
          errorOutput.style.display = "block";
        }
        const sections = document.querySelectorAll(".section");
        sections.forEach((section) => (section.style.display = "none"));
        return;
      }

      let url = `/api/site/${site.id}`;
      doApiCallWithCallback("GET", url, loadSiteCallback, (err) => {
        participantSummaryErrorHandler(err, "#siteErrorOutput");
      });

      url = `/api/client/list/${site.id}`;
      doApiCallWithCallback("GET", url, loadAPIKeysCallback, (err) => {
        participantSummaryErrorHandler(err, "#participantKeysErrorOutput");
      });

      url = `/api/client_side_keypairs/list`;
      doApiCallWithCallback(
        "GET",
        url,
        (r) => {
          loadKeyPairsCallback(r, site.id);
        },
        (err) => {
          participantSummaryErrorHandler(err, "#keyPairsErrorOutput");
        }
      );

      url = "/api/key/list";
      doApiCallWithCallback(
        "GET",
        url,
        (r) => {
          loadEncryptionKeysCallback(r, site.id);
        },
        (err) => {
          participantSummaryErrorHandler(err, "#encryptionKeysErrorOutput");
        }
      );

      url = "/api/operator/list";
      doApiCallWithCallback(
        "GET",
        url,
        (r) => {
          loadOperatorKeysCallback(r, site.id);
        },
        (err) => {
          participantSummaryErrorHandler(err, "#operatorKeysErrorOutput");
        }
      );

      url = "/api/partner_config/get";
      doApiCallWithCallback(
        "GET",
        url,
        (r) => {
          loadOptoutWebhooksCallback(r, site.name);
        },
        (err) => {
          participantSummaryErrorHandler(err, "#webhooksErrorOutput");
        }
      );

      url = `/api/sharing/keysets/related?site_id=${site.id}`;
      doApiCallWithCallback(
        "GET",
        url,
        (r) => {
          loadRelatedKeysetsCallback(r, site.id, site.clientTypes);
        },
        (err) => {
          participantSummaryErrorHandler(err, "#relatedKeysetsErrorOutput");
        }
      );
      const sections = document.querySelectorAll(".section");
      sections.forEach((section) => (section.style.display = "block"));

        const apiKeyUsageGrafanaUrl = `https://uid2.grafana.net/d/JaOQgV7Iz/api-key-usage?orgId=1&from=now-6h&to=now&timezone=browser&var-SiteId=${site.id}&var-Env=prod`;
        const apiKeyUsageElement = document.getElementById("grafanaApiKeyUsage");
        apiKeyUsageElement.href = apiKeyUsageGrafanaUrl;
    });
  }

  const rotateButton = document.getElementById("doRotateKeysets");
  if (rotateButton) {
    rotateButton.addEventListener("click", () => {
      if (!confirm("Are you sure?")) {
        return;
      }

      const keysetsElement = document.getElementById(
        "relatedKeysetsStandardOutput"
      );
      if (keysetsElement) {
        const keysets = keysetsElement.textContent;
        const ja = JSON.parse(keysets);
        ja.forEach((keyset) => {
          const url = `/api/key/rotate_keyset_key?min_age_seconds=1&keyset_id=${keyset.keyset_id}&force=true`;
          doApiCallWithCallback(
            "POST",
            url,
            (r) => {
              rotateKeysetsCallback(r, keyset.keyset_id);
            },
            (err) => {
              rotateKeysetsErrorHandler(err, "#rotateKeysetsErrorOutput");
            }
          );
        });
      }
    });
  }


});

export {};
