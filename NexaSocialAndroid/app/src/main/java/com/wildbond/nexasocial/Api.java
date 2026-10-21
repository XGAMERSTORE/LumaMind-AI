package com.wildbond.nexasocial;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Api {
    public String baseUrl;
    public String token;
    public Api(String baseUrl){ this.baseUrl=trim(baseUrl); }
    static String trim(String s){ if(s==null) return ""; s=s.trim(); while(s.endsWith("/")) s=s.substring(0,s.length()-1); return s; }
    public JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body); }
    public JSONObject get(String path) throws Exception { return request("GET", path, null); }
    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("Accept","application/json");
        if(token!=null&&!token.isEmpty()) c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null){
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code=c.getResponseCode();
        InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();
        if(in!=null){ try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){ String line; while((line=br.readLine())!=null) sb.append(line); } }
        JSONObject out=sb.length()==0?new JSONObject():new JSONObject(sb.toString());
        if(code<200||code>=300) throw new IOException(out.optString("error","HTTP "+code));
        return out;
    }
}
