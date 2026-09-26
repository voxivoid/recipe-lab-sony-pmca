package com.voxivoid.recipelab;

import android.content.Context;
import android.content.res.Resources;

import java.util.HashMap;
import java.util.Map;

/** Android resource adapter for stable text keys. */
final class AndroidTextCatalog implements TextCatalog {
    private final Resources resources;
    private final String packageName;
    private final Map<String, Integer> ids = new HashMap<String, Integer>();

    AndroidTextCatalog(Context context) {
        resources = context.getResources();
        packageName = context.getPackageName();
    }

    public String text(String key, Object... args) {
        Integer cached = ids.get(key);
        int id = cached == null ? resources.getIdentifier(key, "string", packageName) : cached;
        if (id == 0) throw new IllegalArgumentException("Missing string resource: " + key);
        if (cached == null) ids.put(key, id);
        return args.length == 0 ? resources.getString(id) : resources.getString(id, args);
    }
}
