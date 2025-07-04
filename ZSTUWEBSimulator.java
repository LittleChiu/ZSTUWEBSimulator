import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.*;


public class ZSTUWEBSimulator {

    private static final String WEBWPN_URL = "https://sso-443.webvpn.zstu.edu.cn/login";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";


    private final CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(cm)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String lastTicket;


    public void login(String user, String pwd) throws Exception {

        String html = sendGet(WEBWPN_URL);


        String exec   = byId(html, "login-page-flowkey");
        String keyB64 = byId(html, "login-croypto");
        if (exec == null || keyB64 == null)
            throw new IllegalStateException("关键字段缺失！");

        String encPwd = encryptDES(pwd, keyB64);

        String body = "username="   + url(user)   +
                "&password="  + url(encPwd) +
                "&type=UsernamePassword&_eventId=submit" +
                "&geolocation=&captcha_code=" +
                "&execution=" + url(exec) +
                "&croypto="   + url(keyB64);

        HttpRequest req = base(WEBWPN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());

        if (resp.statusCode() != 302)
            throw new IllegalStateException("登录失败，HTTP " + resp.statusCode());

        lastTicket = resp.headers().firstValue("Location").orElseThrow();
        System.out.println("✅ 登录成功，ST = " + lastTicket);
    }

    public String visitService(String serviceBase) throws Exception {
        if (lastTicket == null)
            throw new IllegalStateException("先调用 login()");

        String url = serviceBase + (serviceBase.contains("?") ? "&" : "?")
                + "ticket=" + url(ticketOnly(lastTicket));
        System.out.println(url);
        return sendGet(url);
    }
    public String visit(String serviceBase) throws Exception {
        if (lastTicket == null)
            throw new IllegalStateException("先调用 login()");
        String url = serviceBase;
        System.out.println(url);
        return sendGet(url);
    }

    public String queryGrade(String academicYear, String term) throws Exception {

        String api = "https://jwglxt.webvpn.zstu.edu.cn/jwglxt/cjcx/cjcx_cxXsgrcj.html" +
                "?doType=query&gnmkdm=N305005";

        String form = "xnm=" + academicYear +
                "&xqm=" + term +
                "&sfzgcj=&kcbj=" +
                "&_search=false" +
                "&nd=" + System.currentTimeMillis() +
                "&queryModel.showCount=15" +
                "&queryModel.currentPage=1" +
                "&queryModel.sortName= " +
                "&queryModel.sortOrder=asc" +
                "&time=0";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin",  "https://jwglxt.webvpn.zstu.edu.cn")
                .header("Referer", "https://jwglxt.webvpn.zstu.edu.cn/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("成绩接口状态码：" + resp.statusCode());
        return resp.body();
    }
    private String sendGet(String url) throws Exception {
        HttpRequest req = base(url).GET().build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        return r.body();
    }

    private HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Referer", WEBWPN_URL)
                .header("Origin",  "https://sso.zstu.edu.cn");
    }

    private static String encryptDES(String plain, String keyB64) throws Exception {
        SecretKey k = new SecretKeySpec(Base64.getDecoder().decode(keyB64), "DES");
        Cipher c = Cipher.getInstance("DES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static String byId(String html, String id) {
        Matcher m = Pattern.compile("id=\"" + Pattern.quote(id) + "\"[^>]*>([^<]*)<").matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }
    private static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String ticketOnly(String loc) {
        int p = loc.indexOf("ticket=");
        return p >= 0 ? loc.substring(p + 7) : loc;
    }

    public static void main(String[] args) throws Exception {
        ZSTUWEBSimulator cas = new ZSTUWEBSimulator();
        cas.login("学号", "密码");
        System.out.println(cas.visit("https://webvpn.zstu.edu.cn/"));
        cas.visit("https://webvpn.zstu.edu.cn/vpn_key/update");
        HttpRequest req = cas.base("https://sso-443.webvpn.zstu.edu.cn/login?service=http://jwglxt.zstu.edu.cn/sso/jasiglogin").GET().build();
        HttpResponse<String> r = cas.client.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println(r.body());
        String json = cas.queryGrade("2024", "12"); //根据需要修改学期
        System.out.println(json);
    }
}
