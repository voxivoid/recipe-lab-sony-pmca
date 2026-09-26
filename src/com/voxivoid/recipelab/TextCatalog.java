package com.voxivoid.recipelab;

/** Resolves stable user-interface text keys without coupling camera-free code to Android resources. */
interface TextCatalog {
    String text(String key, Object... args);
}
