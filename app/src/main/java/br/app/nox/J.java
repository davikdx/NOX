package br.app.nox;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

/** Narrow adapter for constructing our own JSON documents. Missing answers stay absent. */
public final class J {
    private J() {}

    public static JSONObject obj(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Pares de chave e valor esperados");
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) put(result, (String) pairs[i], pairs[i + 1]);
        return result;
    }

    public static JSONObject put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
            return target;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Campo inválido: " + key, e);
        }
    }

    public static JSONObject copy(JSONObject target) {
        try { return new JSONObject(target.toString()); }
        catch (JSONException e) { throw new IllegalArgumentException("Registro ilegível", e); }
    }

    public static JSONObject merged(JSONObject defaults, JSONObject stored) {
        if (stored != null) {
            Iterator<String> keys = stored.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                put(defaults, key, stored.opt(key));
            }
        }
        return defaults;
    }
}

