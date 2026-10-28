package com.voxivoid.recipelab.transfer;

import java.lang.reflect.Method;
import java.util.TreeSet;

/**
 * Describes a system service the hotspot looked for, for the card log: its class, what it extends, and its public
 * methods. A body whose Wi-Fi API differs from the A6000's (the A6300 has no "wifi-direct" service) then shows in a
 * user's report what it offers instead. No android.* import may appear here (tools/test.sh).
 */
final class ApiProbe {
    private ApiProbe() {}

    /** a line is wrapped at about this many characters */
    static final int WIDTH = 100;

    /** {@code name: none}, or {@code name: class extends super} then its methods, wrapped */
    static String describe(String name, Object service) {
        if (service == null) return name + ": none";
        Class<?> c = service.getClass();
        StringBuilder sb = new StringBuilder(name).append(": ").append(c.getName());
        Class<?> sup = c.getSuperclass();
        if (sup != null && sup != Object.class) sb.append(" extends ").append(sup.getName());
        return sb.append('\n').append(wrap(methods(c), WIDTH)).toString();
    }

    /** every public method but Object's, as {@code name(Param,Param)}, sorted */
    static TreeSet<String> methods(Class<?> c) {
        TreeSet<String> out = new TreeSet<String>();
        for (Method m : c.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            StringBuilder s = new StringBuilder(m.getName()).append('(');
            Class<?>[] ps = m.getParameterTypes();
            for (int i = 0; i < ps.length; i++) s.append(i == 0 ? "" : ",").append(ps[i].getSimpleName());
            out.add(s.append(')').toString());
        }
        return out;
    }

    /** the items joined by spaces, a new line before one that would pass {@code width} */
    static String wrap(Iterable<String> items, int width) {
        StringBuilder sb = new StringBuilder();
        int col = 0;
        for (String item : items) {
            if (col > 0 && col + 1 + item.length() > width) { sb.append('\n'); col = 0; }
            if (col > 0) { sb.append(' '); col++; }
            sb.append(item);
            col += item.length();
        }
        return sb.toString();
    }
}
