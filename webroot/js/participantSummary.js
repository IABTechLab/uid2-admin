//"plaintext_key": "UID2-C-L-999-Eaqeym.wLHGLzbuoPtVZ4WolwLyTfEo86UPuvzmYuFVg="
//"key_hash": "4mbr0ntMNu7enP2+F/XXdhTIbcG3h2//SAx9qZvStRZS9Lf3HdPaH7F6pHWoc10tPnwyJQLWRgJrSmvFMjKe8A==",
//"secret": "dnU70Zop7WabzK4ZHoDTwFnhN8X6/ikNsMIPElJJsro=",


/* TODO - Validation */
let siteList;

function loadAllSitesCallback(result) {
    //const textToHighlight = '"disabled": true';
    siteList = JSON.parse(result).map(function(item) { return { name: item.name, id: item.id } });

    //const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: orange;">' + textToHighlight + '</span>');
    //$('#participantKeysStandardOutput').html(highlightedText);
    console.log(siteList);
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
    console.log(formatted);
}

function loadAPIKeysCallback(result) {
    const textToHighlight = '"disabled": true';
    const formatted = prettifyJson(result);
    const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: orange;">' + textToHighlight + '</span>');
    $('#participantKeysStandardOutput').html(highlightedText);
};

/* ***** multi-use error handler *** */
function loadAPIKeysError(result) {
    const errorMessage = prettifyJson(result.responseText);
    $('#participantKeysErrorOutput').html(errorMessage);
};

function loadEncryptionKeysCallback(result, siteId) {
    // delineate disabled and expired
    const resultJson = JSON.parse(result);
    const filteredResults = resultJson.filter(function(item) { return item.site_id === siteId });
    const formatted = prettifyJson(JSON.stringify(filteredResults));
    //const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: yellow;">' + textToHighlight + '</span>');
    $('#encryptionKeysStandardOutput').html(formatted);
};

function loadOperatorKeysCallback(result, siteId) {
    const textToHighlight = '"disabled": true';
    const resultJson = JSON.parse(result);
    const filteredResults = resultJson.filter(function(item) { return item.site_id === siteId });
    const formatted = prettifyJson(JSON.stringify(filteredResults));
    const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: orange;">' + textToHighlight + '</span>');
    $('#operatorKeysStandardOutput').html(highlightedText);
};

function loadOptoutWebhooksCallback(result, siteName) {
    const resultJson = JSON.parse(result);
    const filteredResults = resultJson.filter(function(item) { return item.name === siteName });
    const formatted = prettifyJson(JSON.stringify(filteredResults));
    //const highlightedText = formatted.replaceAll(textToHighlight, '<span style="background-color: yellow;">' + textToHighlight + '</span>');
    $('#webhooksStandardOutput').html(formatted);
};

$(document).ready(function () {
    const sitesUrl = '/api/site/list';
    doApiCallWithCallback('GET', sitesUrl, loadAllSitesCallback, loadAPIKeysError);
    
    $('#doSearch').on('click', function () {
        const siteSearch = Number($('#key').val());
        let site = null;
        if (Number.isInteger(siteSearch)) {
            const foundSite = siteList.find(function(item) { return item.id === siteSearch  });
            site = foundSite;
        } else {
            const foundSite = siteList.find(function(item) { return item.name === siteSearch  });
            site = foundSite;
        }
        if (!site) {
            $('#operatorKeysStandardOutput').text(`site not found: ${siteSearch}`);
            return;
        }

        //const siteName = 'ttd';
        let url = `/api/site/${site.id}`;
        doApiCallWithCallback('GET', url, loadSiteCallback, loadAPIKeysError);

        url = `/api/client/list/${site.id}`;
        doApiCallWithCallback('GET', url, loadAPIKeysCallback, loadAPIKeysError);

        url = '/api/key/list';
        doApiCallWithCallback('GET', url, function(r) { loadEncryptionKeysCallback(r, site.id) }, loadAPIKeysError);

        url = '/api/operator/list';
        doApiCallWithCallback('GET', url, function(r) { loadOperatorKeysCallback(r, site.id) }, loadAPIKeysError);

        url = '/api/partner_config/get';
        doApiCallWithCallback('GET', url, function(r) { loadOptoutWebhooksCallback(r, site.name) }, loadAPIKeysError);
    });
});