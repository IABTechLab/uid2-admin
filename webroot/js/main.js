function doApiCall(method, url, outputDiv, errorDiv, body) {
    $(outputDiv).text("");
    $(errorDiv).text("");

    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        data: body,
        success: text => setOutput(text, outputDiv),
        error: err => standardErrorCallback(err, errorDiv)
    });
}

function doApiCallWithCallback(method, url, onSuccess, onError, body) {
    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        data: body,
        success: text => onSuccess(text),
        error: err => onError(err)
    });
}

function setOutput(text, div) {
    const pretty = JSON.stringify(JSON.parse(text), null, 2);
    $(div).text(pretty);
}

function prettifyJson(text) {
    return JSON.stringify(JSON.parse(text), null, 2);
}

function errorCallback(err) {
    standardErrorCallback(err, "#errorOutput")
}

function standardErrorCallback(err, errorDiv, onError) {
    if (err.getResponseHeader("REQUIRES_AUTH") == 1) {
        $("body").hide()
        $("body").replaceWith("Unauthorized, prompting reauthentication...")
        $("body").show()
        $(function () {
            setTimeout(function () {
                window.location.replace("/login");
            }, 3000);
        });
    } else {
        if (onError === undefined) {
            $(errorDiv).text("Error: " + err.status + ": " + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
        } else {
            onError(err);
        }
    }
}

function alertErrorCallback(err, errorDiv, alert) {
    $(errorDiv).text("Error: " + err.status + ": " + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
    $(alert).collapse('show');
}

function isJsonString(str) {
    try {
        JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
}

function clearFormsAndShowCollapsed(toShow) {
    const exclude = toShow.join(',');
    $(".form-clear").not(exclude).text("");
    $("form").trigger("reset");
    $('.collapse').not(exclude).collapse('hide');
    $(".group").removeClass("btn-primary").addClass("btn-secondary");

    setTimeout(function () {
        toShow.forEach((regionId) => {
            $(regionId).collapse('show');
        })
    }, 400);  // the collapse takes 350ms, so wait until all are done before showing them
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
                $(".ro-adm").show();
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
        } else if (window.location.origin.includes("localhost") || (window.location.origin.includes("127.0.0.1"))) {
            header.text(header.text().replace("Env", "Local"));
        } else {
            header.text(header.text().replace("Env", ""));
        }

        if (window.location.origin.endsWith(".eu")) {
            header.text(header.text().replace("UID2", "EUID"));
        }
        
        $(".group").on("click", (e) => {
            $(".group").removeClass("btn-primary").addClass("btn-secondary");
            
            if ($("#" + e.target.id).attr("aria-expanded") == "false") {
                $("#" + e.target.id).removeClass("btn-primary").addClass("btn-secondary");
            } else {
                $("#" + e.target.id).removeClass("btn-secondary").addClass("btn-primary");
            }
        })
    });

}

init();