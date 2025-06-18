let siteList;
let resultsElement = document.querySelector('.search-autocomplete-results');
const searchInput = document.querySelector('#site-search');
searchInput.addEventListener('keyup', searchSitesAutocomplete);

window.addEventListener('mouseup',function(event){
    var dropdown = document.querySelector('.dropdown-menu');
    if(event.target != dropdown && event.target.parentNode != dropdown){
        $(dropdown).hide();
    }
});  

/* ***** multi-use error handler *** */
function participantSummaryErrorHandler(err, divContainer) {
    const errorMessage = prettifyJson(err.responseText);
    $(divContainer).html(errorMessage).show();
};

function loadAllSitesCallback(result) {
    siteList = JSON.parse(result).map((item) => { return { name: item.name, id: item.id, clientTypes: item.clientTypes } });
};

function setSearchValue(searchText) {
    document.querySelector('#site-search').value = searchText;
    $(resultsElement).hide();
    $('#doSearch').click();
}

function searchSitesAutocomplete(e) {
    
    searchString = searchInput.value;
    const options = {
        threshold: .2,
        minMatchCharLength: 2,
        keys: ['id', 'name']
    };
    const fuse = new Fuse(siteList, options);
    const result = fuse.search(searchString).map((site) => {
        return `<a class="dropdown-item" href="#" onclick="setSearchValue('${site.item.name}')">${site.item.name} (${site.item.id})</a>`;
    }) ;

    let resultHtml = '';
    for (let i = 0; i < result.length; i++) {
        resultHtml += result[i];
    }
    resultsElement.innerHTML = resultHtml;
    if (result.length > 0) { 
        $(resultsElement).show();
    } else {
        $(resultsElement).hide();
    }
}

function rotateKeysetsCallback(result, keyset_id) {
    const resultJson = JSON.parse(result);
    const formatted = prettifyJson(JSON.stringify(resultJson));
    $('#rotateKeysetsStandardOutput').append(`keyset_id: ${keyset_id} rotated: <br>${formatted}<br>`);
}

function rotateKeysetsErrorHandler(err, keyset_id) {
    $('#rotateKeysetsErrorOutput').append(`keyset_id: ${keyset_id} rotation failed: <br>${err}<br>`);
}

function loadSiteCallback(result) {
    const resultJson = JSON.parse(result);

    const domainNames = resultJson.domain_names.length > 0 ? resultJson.domain_names : 'none';
    const formattedDomains = prettifyJson(JSON.stringify(domainNames));
    $('#domainNamesStandardOutput').html(formattedDomains);
    const appNames = resultJson.app_names.length > 0 ? resultJson.app_names : 'none';
    const formattedApps = prettifyJson(JSON.stringify(appNames));
    $('#appNamesStandardOutput').html(formattedApps);
    delete resultJson.domain_names;
    delete resultJson.app_names;
    let formatted = JSON.stringify(resultJson);
    formatted = prettifyJson(formatted);
    $('#siteStandardOutput').html(formatted);
}

function formatUnixDate(unixTime) {
    const time = unixTime < 1e12 ? unixTime * 1000 : unixTime;
    return time;
}

function loadAPIKeysCallback(result) {
    const textToHighlight = '"disabled": true';
    let resultJson = JSON.parse(result);
    resultJson = resultJson.map((item) => {
        const created = new Date(formatUnixDate(item.created)).toLocaleString();
        return { ...item, created };
    });
    const formatted = prettifyJson(JSON.stringify(resultJson));
    const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: orange;">' + textToHighlight + '</span>');
    $('#participantKeysStandardOutput').html(highlightedText);
};

function loadKeyPairsCallback(result, siteId) {
    let resultJson = JSON.parse(result);
    let filteredResults = resultJson.filter((item) => { return item.site_id === siteId });
    const formatted = prettifyJson(JSON.stringify(filteredResults));
    $('#keyPairsStandardOutput').html(formatted);
};

function loadEncryptionKeysCallback(result, siteId) {
    const resultJson = JSON.parse(result);
    let filteredResults = resultJson.filter((item) => { return item.site_id === siteId });
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
        highlightedText = highlightedText.replaceAll(item, '<span style="background-color: orange;">' + item + '</span>');
    });
    notActivated.forEach((item) => {
        highlightedText = highlightedText.replaceAll(item, '<span style="background-color: yellow;">' + item + '</span>');
    });
    $('#encryptionKeysStandardOutput').html(highlightedText);
};

function loadOperatorKeysCallback(result, siteId) {
    const textToHighlight = '"disabled": true';
    const resultJson = JSON.parse(result);
    let filteredResults = resultJson.filter((item) => { return item.site_id === siteId });
    filteredResults = filteredResults.map((item) => { 
        const created = new Date(item.created).toLocaleString();
        return { ...item, created };
     });

    const formatted = prettifyJson(JSON.stringify(filteredResults));
    const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: orange;">' + textToHighlight + '</span>');
    $('#operatorKeysStandardOutput').html(highlightedText);
};

function loadOptoutWebhooksCallback(result, siteName) {
    const resultJson = JSON.parse(result);
    const filteredResults = resultJson.filter((item) => { return item.name === siteName });
    const formatted = prettifyJson(JSON.stringify(filteredResults));
    $('#webhooksStandardOutput').html(formatted);
};

function loadRelatedKeysetsCallback(result, siteId, clientTypes) {
    const resultJson = JSON.parse(result);
    resultJson.forEach(obj => {
        // Keysets where allowed_types include any of the clientTypes that belows to the site
        obj.allowed_types = obj.allowed_types.map(item => {
            return clientTypes.includes(item) ? `<allowed_types_${item}>` : item;
        });

        // Keysets where allowed_sites include the leaked site. As it's an integer object, change it to a placeholder and replace later.
        if (obj.allowed_sites) {
            obj.allowed_sites = obj.allowed_sites.map(item => {
                return item === siteId ? "<allowed_sites_matched>" : item;
            });
        }
    });
    const formatted = prettifyJson(JSON.stringify(resultJson));
    let highlightedText = formatted;
    // Highlight ketsets where allowed_sites is set to null
    highlightedText = highlightedText.replaceAll(`"allowed_sites": null`, '<span style="background-color: orange;">' + `"allowed_sites": null` + '</span>');
    // Highlight keysets where allowed_types include the site client_types
    highlightedText = highlightedText.replaceAll(`"<allowed_types_DSP>"`, `<span style="background-color: orange;">"DSP"</span>`);
    highlightedText = highlightedText.replaceAll(`"<allowed_types_ADVERTISER>"`, `<span style="background-color: orange;">"ADVERTISER"</span>`);
    highlightedText = highlightedText.replaceAll(`"<allowed_types_DATA_PROVIDER>"`, `<span style="background-color: orange;">"DATA_PROVIDER"</span>`);
    highlightedText = highlightedText.replaceAll(`"<allowed_types_PUBLISHER>"`, `<span style="background-color: orange;">"PUBLISHER"</span>`);
    // Highlight keysets where allowed_sites include the leaked site
    highlightedText = highlightedText.replaceAll(`"<allowed_sites_matched>"`, `<span style="background-color: orange;">${siteId}</span>`);
    // Highlight keysets belonging to the leaked site itself
    highlightedText = highlightedText.replaceAll(`"site_id": ${siteId}`, '<span style="background-color: orange;">' + `"site_id": ${siteId}` + '</span>');
    $('#relatedKeysetsStandardOutput').html(highlightedText);
};

$(document).ready(() => {
    const sitesUrl = '/api/site/list';
    doApiCallWithCallback('GET', sitesUrl, loadAllSitesCallback, null);
    
    $('#doSearch').on('click', () => {
        $('#siteSearchErrorOutput').hide();
        const siteSearch = $('#site-search').val();
        let site = null;
        if (Number.isInteger(Number(siteSearch))) {
            const foundSite = siteList.find((item) => { return item.id === Number(siteSearch) });
            site = foundSite;
        } else {
            const foundSite = siteList.find((item) => { return item.name === siteSearch  });
            site = foundSite;
        }
        if (!site) {
            $('#siteSearchErrorOutput').text(`site not found: ${siteSearch}`).show();
            $('.section').hide();
            return;
        }

        let url = `/api/site/${site.id}`;
        doApiCallWithCallback('GET', url, loadSiteCallback, (err) => { participantSummaryErrorHandler(err, '#siteErrorOutput') });

        url = `/api/client/list/${site.id}`;
        doApiCallWithCallback('GET', url, loadAPIKeysCallback, (err) => { participantSummaryErrorHandler(err, '#participantKeysErrorOutput') });

        url = `/api/client_side_keypairs/list`;
        doApiCallWithCallback('GET', url, (r) => { loadKeyPairsCallback(r, site.id) }, (err) => { participantSummaryErrorHandler(err, '#keyPairsErrorOutput') });

        url = '/api/key/list';
        doApiCallWithCallback('GET', url, (r) => { loadEncryptionKeysCallback(r, site.id) }, (err) => { participantSummaryErrorHandler(err, '#encryptionKeysErrorOutput') });

        url = '/api/operator/list';
        doApiCallWithCallback('GET', url, (r) => { loadOperatorKeysCallback(r, site.id) }, (err) => { participantSummaryErrorHandler(err, '#operatorKeysErrorOutput') });

        url = '/api/partner_config/get';
        doApiCallWithCallback('GET', url, (r) => { loadOptoutWebhooksCallback(r, site.name) }, (err) => { participantSummaryErrorHandler(err, '#webhooksErrorOutput') });

        url = `/api/sharing/keysets/related?site_id=${site.id}`;
        doApiCallWithCallback('GET', url, (r) => { loadRelatedKeysetsCallback(r, site.id, site.clientTypes) }, (err) => { participantSummaryErrorHandler(err, '#relatedKeysetsErrorOutput') });
        $('.section').show();
    });

    $('#doRotateKeysets').on('click', () => {
        if (!confirm("Are you sure?")) {
            return;
        }

        var keysets = $('#relatedKeysetsStandardOutput').text();
        const ja = JSON.parse(keysets);
        var rotateKeysetsMessage = '';
        ja.forEach((keyset) => {
            var url = `/api/key/rotate_keyset_key?min_age_seconds=1&keyset_id=${keyset.keyset_id}&force=true`;
            doApiCallWithCallback(
                'POST',
                url,
                (r) => { rotateKeysetsCallback(r, keyset.keyset_id) },
                (err) => { rotateKeysetsErrorHandler(err, '#rotateKeysetsErrorOutput') });
        });
    });
});