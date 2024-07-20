"use strict"

/** Manage browser-local storage */
class LocalSettings {
    /* get last-used environment */
    get environment() {
        if (typeof (Storage) !== "undefined") {
            return localStorage.getItem("environment");
        }
        else {
            return null;
        }
    }

    /* save last-used environment */
    set environment(value) {
        if (typeof (Storage) !== "undefined") {
            localStorage.setItem("environment", value);
        }
    }
}

class Model {
    _getHeaders() {
        return { "X-JITACCESS": "1" };
    }

    _formatError(error) {
        let message = (error.responseJSON && error.responseJSON.message)
            ? error.responseJSON.message
            : "";
        return `${message} (HTTP ${error.status}: ${error.statusText})`;
    }

    get context() {
        console.assert(this._context);
        return this._context;
    }

    async initialize(environment, resource) {
        console.assert(environment);
        console.assert(resource);

        this.environment = environment;
        this.resource = resource;

        try {
            const contextCall = $.ajax({
                url: "/api/user/context",
                dataType: "json",
                headers: this._getHeaders()
            });
            const contentCall = $.ajax({
                url: `/api/catalog${resource}`,
                dataType: "json",
                headers: this._getHeaders()
            });

            this._context = await contextCall;
            this.content = await contentCall;
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    async listEnvironments() {
        try {
            return await $.ajax({
                url: "/api/catalog/environments",
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    async postback(data, resource = null) {
        try {
            return await $.ajax({
                url: `/api/catalog${resource ? resource : this.resource}`,
                type: 'POST',
                data: data,
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }
}

class DebugModel extends Model {
    constructor() {
        super();
        $("body").append(`
            <div id="debug-pane">
                <div>
                User: <input type="text" id="debug-principal"/>
                </div>
                <hr/>
                <div>
                    listEnvironments:
                    <select id="debug-listEnvironments">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="10">Simulate 10 result</option>
                        <option value="100">Simulate 100 results</option>
                    </select>
                </div>
            </div>
        `);

        //
        // Persist settings.
        //
        [
            "debug-principal",
            "debug-listEnvironments"
        ].forEach(setting => {
            $("#" + setting).val(localStorage.getItem(setting))
            $("#" + setting).change(() => {
                localStorage.setItem(setting, $("#" + setting).val());
            });
        });
    }

    _getHeaders() {
        const headers = super._getHeaders();
        const user = $("#debug-principal").val();
        if (user) {
            headers["X-debug-principal"] = user;
        }
        return headers;
    }

    async _simulateError() {
        await new Promise(r => setTimeout(r, 1000));
        return Promise.reject("Simulated error");
    }

    async listEnvironments() {
        var setting = $("#debug-listEnvironments").val();
        if (!setting) {
            return super.listEnvironments();
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 2000));
            return Promise.resolve({
                environments: Array.from(
                    { length: setting },
                    (e, i) => {
                        return {
                            name: `environment-${i}`,
                            description: `Debug environment-${i}`
                        };
                    })
            });
        }
    }

}