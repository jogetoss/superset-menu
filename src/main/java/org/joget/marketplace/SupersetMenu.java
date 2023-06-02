package org.joget.marketplace;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.marketplace.model.ApiResponse;
import org.joget.marketplace.model.GuestToken;
import org.joget.marketplace.model.Login;
import org.joget.marketplace.model.Resource;
import org.joget.marketplace.model.SupersetUser;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONObject;

public class SupersetMenu extends UserviewMenu implements PluginWebSupport {
    private final static String MESSAGE_PATH = "messages/SupersetMenu";
 
    private static final String DASHBOARD_PUBLIC = "public";
    private static final String DASHBOARD_PROTECTED = "protected";


    public String getName() {
        return AppPluginUtil.getMessage("userview.superset.name", getClassName(), MESSAGE_PATH);
    }

    public String getVersion() {
        final Properties projectProp = new Properties();
        try {
            projectProp.load(this.getClass().getClassLoader().getResourceAsStream("project.properties"));
        } catch (IOException ex) {
            LogUtil.error(getClass().getName(), ex, "Unable to get project version from project properties...");
        }
        return projectProp.getProperty("version");
    }
    
    public String getClassName() {
        return getClass().getName();
    }

    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.superset.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.superset.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/SupersetMenu.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String getCategory() {
        return "Marketplace";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-chart-bar\"></i>";
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public String getRenderPage() {
        Map freeMarkerModel = new HashMap();
        freeMarkerModel.put("request", getRequestParameters());
        freeMarkerModel.put("element", this);

        String dashboardType = getPropertyString("type");
        if (DASHBOARD_PUBLIC.equalsIgnoreCase(dashboardType)) {
            setProperty("dashboardUrl", getPropertyString("dashboardUrl"));
        } else if (DASHBOARD_PROTECTED.equalsIgnoreCase(dashboardType)) {
            String apacheSupersetUrl = getPropertyString("url");
            if (apacheSupersetUrl.endsWith("/")) {
                apacheSupersetUrl = apacheSupersetUrl.substring(0, apacheSupersetUrl.length() - 1);
            }
            String username = getPropertyString("username");
            String password = getPropertyString("password");
            String embedId = getPropertyString("embedId");
            
            setProperty("embedId", embedId);
            setProperty("apacheSupersetUrl", apacheSupersetUrl);
            password = SecurityUtil.decrypt(password);
            String guestToken = generateGuestToken(username, password, apacheSupersetUrl, embedId);
            setProperty("guestToken", guestToken);
        }

        setProperty("dashboardType", dashboardType);
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        String content = pluginManager.getPluginFreeMarkerTemplate(freeMarkerModel, getClass().getName(), "/templates/SupersetMenu.ftl", null);
        return content;
    }

    public String generateGuestToken(String username, String password, String supersetUrl, String embedId) {
        String guestToken = "";
        ApiResponse loginResponse = callLoginApi(supersetUrl + "/api/v1/security/login", username, password);
        if (loginResponse != null) {
            if (loginResponse.getResponseCode() == 200) {
                String res = loginResponse.getResponseBody();
                JSONObject loginResObject = new JSONObject(res);
                String accessToken = (String) loginResObject.get("access_token");
                if (accessToken != null && !accessToken.isEmpty()) {
                    // call api/v1/security/csrf_token API
                    ApiResponse csrfResponse = callCsrfTokenApi(supersetUrl + "/api/v1/security/csrf_token/", accessToken);
                    if (csrfResponse != null) {
                        if (csrfResponse.getResponseCode() == 200) {
                            String csrfRes = csrfResponse.getResponseBody();
                            JSONObject csrfResObject = new JSONObject(csrfRes);
                            String csrfToken = (String) csrfResObject.get("result");
                            ApiResponse gustTokenResponse = callGuestTokenApi(supersetUrl + "/api/v1/security/guest_token/", accessToken, csrfToken, username, password, embedId); //token
                            if (gustTokenResponse != null) {
                                if (gustTokenResponse.getResponseCode() == 200) {
                                    String guestTokenRes = gustTokenResponse.getResponseBody();
                                    JSONObject guestTokenResObject = new JSONObject(guestTokenRes);
                                    guestToken = (String) guestTokenResObject.get("token");
                                    LogUtil.info(getClassName(), "guestToken: " + guestToken);
                                } else {
                                    LogUtil.info(getClassName(), gustTokenResponse.getResponseBody());
                                }
                            }
                        } else {
                            LogUtil.info(getClassName(), csrfResponse.getResponseBody());
                        }
                    }
                }
            } else {
                LogUtil.info(getClassName(), loginResponse.getResponseBody());
            }
        }
        return guestToken;
    }

    public ApiResponse callCsrfTokenApi(String endPoint, String bearerToken) {
        ApiResponse apiResponse = null;
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpGet getRequest = new HttpGet(endPoint);
        getRequest.setHeader("Authorization", "Bearer " + bearerToken);
        try {
            apiResponse = new ApiResponse();
            HttpResponse response = httpClient.execute(getRequest);
            apiResponse.setResponseCode(response.getStatusLine().getStatusCode());
            apiResponse.setResponseBody(EntityUtils.toString(response.getEntity()));
        } catch (IOException ex) {
            LogUtil.error(SupersetMenu.class.getName(), ex, ex.getMessage());
        }
        return apiResponse;
    }

    public ApiResponse callGuestTokenApi(String endPoint, String bearerToken, String csrfToken, String username, String password, String embedId) {

        ApiResponse apiResponse = null;
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost postRequest = new HttpPost(endPoint);
        postRequest.setHeader("Content-Type", "application/json");
        postRequest.setHeader("X-CSRF-Token", csrfToken);
        postRequest.setHeader("Authorization", "Bearer " + bearerToken);
        GuestToken guestToken = new GuestToken();

        List<Resource> resources = new ArrayList<>();
        Resource resource = new Resource();
        resource.setId(embedId);
        resource.setType("dashboard");
        resources.add(resource);
        guestToken.setResources(resources);

        SupersetUser user = new SupersetUser();
        user.setFirstName("SUPERSET_FIRST_NAME");
        user.setLastName("SUPERSET_LAST_NAME");
        user.setUsername(username);
        guestToken.setUser(user);
        guestToken.setRls(new ArrayList<Object>());
        Gson gson = new Gson();
        String requestBody = gson.toJson(guestToken);
        StringEntity params;

        try {
            apiResponse = new ApiResponse();
            params = new StringEntity(requestBody);
            postRequest.setEntity(params);
            HttpResponse response = httpClient.execute(postRequest);
            apiResponse.setResponseCode(response.getStatusLine().getStatusCode());
            apiResponse.setResponseBody(EntityUtils.toString(response.getEntity()));
        } catch (UnsupportedEncodingException ex) {
            LogUtil.error(SupersetMenu.class.getName(), ex, ex.getMessage());
        } catch (IOException ex) {
            LogUtil.error(SupersetMenu.class.getName(), ex, ex.getMessage());
        }
        return apiResponse;
    }

    public ApiResponse callLoginApi(String endPoint, String username, String password) {
        ApiResponse apiResponse = null;
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost postRequest = new HttpPost(endPoint);
        postRequest.addHeader("Content-Type", "application/json");
        Login login = new Login();
        login.setUsername(username);
        login.setPassword(password);
        login.setProvider("db");
        Gson gson = new Gson();
        String requestBody = gson.toJson(login);
        StringEntity params;
        try {
            apiResponse = new ApiResponse();
            params = new StringEntity(requestBody);
            postRequest.setEntity(params);
            HttpResponse response = httpClient.execute(postRequest);
            apiResponse.setResponseCode(response.getStatusLine().getStatusCode());
            apiResponse.setResponseBody(EntityUtils.toString(response.getEntity()));
        } catch (UnsupportedEncodingException ex) {
            LogUtil.error(SupersetMenu.class.getName(), ex, ex.getMessage());
        } catch (IOException ex) {
            LogUtil.error(SupersetMenu.class.getName(), ex, ex.getMessage());
        }
        return apiResponse;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // do nothing
    }

}
