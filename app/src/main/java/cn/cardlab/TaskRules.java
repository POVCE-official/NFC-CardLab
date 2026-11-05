package cn.cardlab;

import java.net.URI;
import java.util.Locale;

final class TaskRules {
    static String url(String raw, boolean website) {
        String s=raw.trim();
        if(website && !s.contains(":")) s="https://"+s;
        try {
            URI uri=new URI(s); String scheme=uri.getScheme();
            if(scheme==null) throw new IllegalArgumentException("请填写完整链接，例如 myapp://page");
            scheme=scheme.toLowerCase(Locale.ROOT);
            if(website && !(scheme.equals("https")||scheme.equals("http"))) throw new IllegalArgumentException("网址需以 https:// 或 http:// 开头");
            if(java.util.Arrays.asList("intent","javascript","file","content","data").contains(scheme)) throw new IllegalArgumentException("不支持此链接类型，请使用 App 提供的页面链接");
            if((scheme.equals("https")||scheme.equals("http"))&&(uri.getHost()==null||uri.getHost().isEmpty())) throw new IllegalArgumentException("网址缺少有效域名");
            return uri.toASCIIString();
        }catch(java.net.URISyntaxException e){throw new IllegalArgumentException("链接格式不正确，请检查空格等字符");}
    }
    static String packageName(String raw) {
        String s=raw.trim();
        if(!s.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) throw new IllegalArgumentException("请输入有效的 App 包名，例如 com.example.app");
        return s;
    }
}
