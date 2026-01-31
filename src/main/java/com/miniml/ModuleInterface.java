package com.miniml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleInterface {
    private final Map<String, Type> exports = new HashMap<>();
    
    public void addExport(String name, Type type) {
        exports.put(name, type);
    }
    
    public Map<String, Type> getExports() {
        return new HashMap<>(exports);
    }
    
    public Type getType(String name) {
        return exports.get(name);
    }
    
    public void writeToFile(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Type> entry : exports.entrySet()) {
            sb.append(entry.getKey()).append(" : ").append(typeToString(entry.getValue())).append("\n");
        }
        Files.writeString(path, sb.toString());
    }
    
    public static ModuleInterface readFromFile(Path path) throws IOException {
        ModuleInterface iface = new ModuleInterface();
        if (!Files.exists(path)) {
            return iface;
        }
        
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int colonIndex = line.indexOf(" : ");
            if (colonIndex == -1) {
                continue;
            }
            
            String name = line.substring(0, colonIndex).trim();
            String typeStr = line.substring(colonIndex + 3).trim();
            Type type = parseTypeString(typeStr);
            iface.addExport(name, type);
        }
        
        return iface;
    }
    
    private static String typeToString(Type type) {
        return switch (type) {
            case Type.TInt() -> "int";
            case Type.TDouble() -> "double";
            case Type.TBool() -> "bool";
            case Type.TString() -> "string";
            case Type.TUnit() -> "unit";
            case Type.TList(Type elementType) -> typeToString(elementType) + " list";
            case Type.TFun(Type param, Type result) -> {
                String paramStr = param instanceof Type.TFun ? "(" + typeToString(param) + ")" : typeToString(param);
                yield paramStr + " -> " + typeToString(result);
            }
            case Type.TVar(String name) -> name;
            case Type.TNumeric(String name) -> "numeric";
            case Type.TScheme(List<String> vars, Type innerType) -> typeToString(innerType);
        };
    }
    
    private static Type parseTypeString(String typeStr) {
        typeStr = typeStr.trim();
        
        int arrowIndex = findTopLevelArrow(typeStr);
        if (arrowIndex != -1) {
            String paramStr = typeStr.substring(0, arrowIndex).trim();
            String resultStr = typeStr.substring(arrowIndex + 2).trim();
            
            if (paramStr.startsWith("(") && paramStr.endsWith(")")) {
                paramStr = paramStr.substring(1, paramStr.length() - 1);
            }
            
            Type paramType = parseTypeString(paramStr);
            Type resultType = parseTypeString(resultStr);
            return new Type.TFun(paramType, resultType);
        }
        
        if (typeStr.endsWith(" list")) {
            String elementTypeStr = typeStr.substring(0, typeStr.length() - 5).trim();
            Type elementType = parseTypeString(elementTypeStr);
            return new Type.TList(elementType);
        }
        
        return switch (typeStr) {
            case "int" -> new Type.TInt();
            case "double" -> new Type.TDouble();
            case "bool" -> new Type.TBool();
            case "string" -> new Type.TString();
            case "unit" -> new Type.TUnit();
            default -> new Type.TVar(typeStr);
        };
    }
    
    private static int findTopLevelArrow(String typeStr) {
        int depth = 0;
        for (int i = 0; i < typeStr.length() - 1; i++) {
            char c = typeStr.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && c == '-' && typeStr.charAt(i + 1) == '>') {
                return i;
            }
        }
        return -1;
    }
}
