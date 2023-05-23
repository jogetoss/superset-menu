<script src="https://unpkg.com/@superset-ui/embedded-sdk"></script>
<fieldset id="form-canvas">
    <div class="form-section">
        <div class="form-section-title"><span>${element.properties.label}</span></div>
        
        <#if (element.properties.dashboardType! == 'public') >
            <style>
                .right-button-panel { display: none; }
            </style>
            <iframe src="${element.properties.dashboardUrl}?standalone=true" id="public-ss-dashboard" style="width:100% !important; height:100vh !important; border: 1px solid !important; margin: 10px !important;"></iframe>
        </#if>
        <#if (element.properties.dashboardType! == 'protected') >
            <div>
                <div id="my-superset-container" style="display: flex; align-items: center; justify-content: center;"></div>
            </div>
        </#if>
    </div>
</fieldset>

<script>
    //iFrameResize({ log: true }, '#public-ss-dashboard');
    $('#public-ss-dashboard').iFrameResize();
</script>

<script>
    $(document).ready(function(){
        <#if (element.properties.dashboardType! == 'protected') >
            const myDashboard = supersetEmbeddedSdk.embedDashboard({
                id: "${element.properties.embedId}",
                supersetDomain: "${element.properties.apacheSupersetUrl}",
                mountPoint: document.getElementById("my-superset-container"),
                fetchGuestToken: () => '${element.properties.guestToken}',
                dashboardUiConfig: {
                    hideTitle: ${element.properties.hideTitle},
                    hideTab: ${element.properties.hideTab},
                    hideChartControls: ${element.properties.hideChartControls},
                    filters: {
                        expanded: true,
                    }
                }
            });
            // Wait for the dashboard to be embedded
            myDashboard.then(() => {
                const iframe = document.getElementById("my-superset-container").querySelector("iframe");
                iframe.style.width = "78vw";
                iframe.style.height = "100vh";
                iframe.style.margin = "10px";
                iframe.style.border = "1px solid #000000";
                iframe.style.borderStyle = "solid";
            });
        </#if>
    });
</script>