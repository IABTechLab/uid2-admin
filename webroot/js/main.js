function doApiCall(method, url, outputDiv, errorDiv, body) {
    $(outputDiv).text("");
    $(errorDiv).text("");

    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        data : body,
        success: text => setOutput(text, outputDiv),
        error: err => standardErrorCallback(err, errorDiv)
    });
}

function doApiCallWithCallback(method, url, onSuccess, onError, body) {
    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        data : body,
        success: text => onSuccess(text),
        error: err => onerror(err)
    });
}

function setOutput(text, div) {
    const pretty = JSON.stringify(JSON.parse(text), null, 2);
    $(div).text(pretty);
}

function errorCallback(err) { 
    standardErrorCallback(err, "#errorOutput") 
}

function standardErrorCallback(err, errorDiv, onError) {
    if(err.getResponseHeader("REQUIRES_AUTH") == 1) {
        $("body").hide()
        $("body").replaceWith("Unauthorized, prompting reauthentication...")
        $("body").show()
        $(function () {
            setTimeout(function() {
                window.location.replace("/login");
            }, 3000);
        });
    } else {
        if(onError === undefined) {
            $(errorDiv).text("Error: " + err.status + ": " + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
        } else {
            onError(err);
        }
    }
}

function isJsonString(str) {
    try {
        JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
}

function init() {
    $.ajax({
        type: "GET",
        url: "/api/userinfo",
        dataType: "text",
        success: function (text) {
            const u = JSON.parse(text);

            $("#loginEmail").text(u.email);
            $(".authed").show();

            if (u.groups.findIndex(e => e === "developer" || e === "developer-elevated" || e === "infra-admin" || e === "admin") >= 0) {
                $(".ro-cki").show();
                $(".ro-opm").show();
                $(".ro-adm").show();
                $(".ro-sem").show();
            }
            if (u.groups.length === 0) {
                $(".ro-nil").show();
            }
        },
        error: err => {
            // alert("Error: " + err.status + ": " + JSON.parse(err).message);
            $(".notauthed").show();
        }
    });

    $(document).ready(() => {
        const header = $("h1").first();

        if (window.location.origin.includes("prod")) {
            header.text(header.text().replace("Env", "Prod"));
        } else if (window.location.origin.includes("integ")) {
            header.text(header.text().replace("Env", "Integ"));
        } else if (window.location.origin.includes("localhost")) {
            header.text(header.text().replace("Env", "Dev"));
        } else {
            header.text(header.text().replace("Env", ""));
        }

        if (window.location.origin.endsWith(".eu")) {
            header.text(header.text().replace("UID2", "EUID"));
        }
    });
}

init();