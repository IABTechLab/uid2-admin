let siteList;

/* ***** multi-use error handler *** */
function participantSummaryErrorHandler(err, divContainer) {
    const errorMessage = prettifyJson(err.responseText);
    $(divContainer).html(errorMessage).show();
};

function loadAllSitesCallback(result) {
    siteList = JSON.parse(result).map((item) => { return { name: item.name, id: item.id } });
};

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

function loadAPIKeysCallback(result) {
    const textToHighlight = '"disabled": true';
    let resultJson = JSON.parse(result);
    resultJson = resultJson.map((item) => { 
        const created = new Date(item.created).toLocaleString();
        return { ...item, created };
    });
    const formatted = prettifyJson(JSON.stringify(resultJson));
    const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: orange;">' + textToHighlight + '</span>');
    $('#participantKeysStandardOutput').html(highlightedText);
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

$(document).ready(() => {
    const sitesUrl = '/api/site/list';
    doApiCallWithCallback('GET', sitesUrl, loadAllSitesCallback, null);
    
    $('#doSearch').on('click', () => {
        $('#siteSearchErrorOutput').hide();
        const siteSearch = $('#key').val();
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

        url = '/api/key/list';
        doApiCallWithCallback('GET', url, (r) => { loadEncryptionKeysCallback(r, site.id) }, (err) => { participantSummaryErrorHandler(err, '#encryptionKeysErrorOutput') });

        url = '/api/operator/list';
        doApiCallWithCallback('GET', url, (r) => { loadOperatorKeysCallback(r, site.id) }, (err) => { participantSummaryErrorHandler(err, '#operatorKeysErrorOutput') });

        url = '/api/partner_config/get';
        doApiCallWithCallback('GET', url, (r) => { loadOptoutWebhooksCallback(r, site.name) }, (err) => { participantSummaryErrorHandler(err, '#webhooksErrorOutput') });
    
        $('.section').show();
    });
});