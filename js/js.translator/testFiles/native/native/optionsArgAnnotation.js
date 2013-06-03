function getCookies(options, callback) {
    return options.url + callback();
}

function setTitle(options) {
    return options.title + (options.hasOwnProperty("tabId") ? options.tabId : "");
}