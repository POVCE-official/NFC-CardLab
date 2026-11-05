package cn.cardlab;
import org.junit.Test;
import static org.junit.Assert.*;
public class TaskRulesTest {
    @Test public void addsHttpsToWebsite(){assertEquals("https://example.com/path",TaskRules.url(" example.com/path ",true));}
    @Test public void preservesAppPage(){assertEquals("myapp://page/123",TaskRules.url("myapp://page/123",false));}
    @Test public void encodesUnicodePath(){assertEquals("https://example.com/%E4%BD%A0%E5%A5%BD",TaskRules.url("https://example.com/你好",true));}
    @Test(expected=IllegalArgumentException.class) public void rejectsJavascript(){TaskRules.url("javascript:alert(1)",false);}
    @Test(expected=IllegalArgumentException.class) public void rejectsIntentPayload(){TaskRules.url("intent://test/#Intent;end",false);}
    @Test(expected=IllegalArgumentException.class) public void rejectsNonWebWebsite(){TaskRules.url("myapp://page",true);}
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingHost(){TaskRules.url("https:///page",true);}
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingDeepScheme(){TaskRules.url("myapp/page",false);}
    @Test public void acceptsPackage(){assertEquals("com.example.app",TaskRules.packageName("com.example.app"));}
    @Test(expected=IllegalArgumentException.class) public void rejectsPackageInjection(){TaskRules.packageName("com.app;action=bad");}
}
