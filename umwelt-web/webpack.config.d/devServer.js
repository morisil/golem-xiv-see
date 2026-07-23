// Applied to every webpack invocation, including jsBrowserDistribution — so
// only devServer settings belong here (webpack ignores devServer outside the
// dev server task); never override config.mode, the Kotlin plugin sets it
// per task (development for jsBrowserDevelopmentRun, production for the
// distribution that gets embedded into the server binary).
;(function(config) {
    if(!config.hasOwnProperty('devServer')) {
        config.devServer = { }
    }
    // SPA deep links: serve index.html for history-API routes like
    // /sessions/<uuid>, so a reload or a pasted URL boots the app and the
    // router resolves the path client-side. Asset requests are unaffected
    // (paths with a dot are excluded from the fallback by default).
    config.devServer.historyApiFallback = true
})(config);