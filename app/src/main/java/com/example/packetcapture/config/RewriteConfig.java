package com.example.packetcapture.config;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewriteConfig {
    private static final String TAG = "RewriteConfig";
    
    private List<RewriteRule> rules;
    
    public RewriteConfig() {
        rules = new ArrayList<>();
    }
    
    public List<RewriteRule> getRules() {
        return rules;
    }
    
    public boolean loadConfigFromFile(File configFile) {
        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            
            return parseConfig(content.toString());
        } catch (IOException e) {
            Log.e(TAG, "Error reading config file", e);
            return false;
        }
    }
    
    public boolean parseConfig(String configJson) {
        try {
            rules.clear();
            JSONArray rulesArray = new JSONArray(configJson);
            
            for (int i = 0; i < rulesArray.length(); i++) {
                JSONObject ruleObj = rulesArray.getJSONObject(i);
                
                String name = ruleObj.getString("name");
                boolean enabled = ruleObj.getBoolean("enabled");
                String url = ruleObj.getString("url");
                String type = ruleObj.getString("type");
                
                RewriteRule rule = new RewriteRule(name, enabled, url, type);
                
                // 解析重写项
                JSONArray itemsArray = ruleObj.getJSONArray("items");
                for (int j = 0; j < itemsArray.length(); j++) {
                    JSONObject itemObj = itemsArray.getJSONObject(j);
                    boolean itemEnabled = itemObj.getBoolean("enabled");
                    String itemType = itemObj.getString("type");
                    
                    RewriteItem item = new RewriteItem(itemEnabled, itemType);
                    
                    // 解析值
                    JSONObject valuesObj = itemObj.getJSONObject("values");
                    Map<String, String> values = new HashMap<>();
                    
                    if (itemType.equals("replaceResponseBody") && valuesObj.has("body")) {
                        values.put("body", valuesObj.getString("body"));
                    } else if (itemType.equals("replaceResponseHeader")) {
                        // 处理响应头替换
                        // 这里可以添加更多的处理逻辑
                    } else if (itemType.equals("replaceResponseStatus")) {
                        // 处理响应状态码替换
                    }
                    
                    item.setValues(values);
                    rule.addItem(item);
                }
                
                rules.add(rule);
            }
            
            Log.d(TAG, "Loaded " + rules.size() + " rewrite rules");
            return true;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing config JSON", e);
            return false;
        }
    }
    
    public static class RewriteRule {
        private String name;
        private boolean enabled;
        private String url;
        private String type;
        private List<RewriteItem> items;
        
        public RewriteRule(String name, boolean enabled, String url, String type) {
            this.name = name;
            this.enabled = enabled;
            this.url = url;
            this.type = type;
            this.items = new ArrayList<>();
        }
        
        public void addItem(RewriteItem item) {
            items.add(item);
        }
        
        public String getName() {
            return name;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public String getUrl() {
            return url;
        }
        
        public String getType() {
            return type;
        }
        
        public List<RewriteItem> getItems() {
            return items;
        }
    }
    
    public static class RewriteItem {
        private boolean enabled;
        private String type;
        private Map<String, String> values;
        
        public RewriteItem(boolean enabled, String type) {
            this.enabled = enabled;
            this.type = type;
            this.values = new HashMap<>();
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public String getType() {
            return type;
        }
        
        public Map<String, String> getValues() {
            return values;
        }
        
        public void setValues(Map<String, String> values) {
            this.values = values;
        }
    }
} 