---
source: https://docs.usercentrics.com/#/consent-mode
---
> [← Back to Feature Overview](browser-cmp?id=features)

# Google Consent Mode

!> **Google released Consent Mode v2 at the end of November 2023.** Version 2 of Consent Mode introduces two additional bits (ad_user_data & ad_personalization) within the consent mode updates. Usercentrics supports these new bits in the update signals as a standard feature. Please ensure that you are also incorporating updates to these two new bits within your default state implementation! If you are using the implementation through our Google Tag Manager community template, please update to the newest version that supports Consent Mode v2.


## General Information
With [Consent Mode](https://support.google.com/analytics/answer/9976101?hl=en) Google has provided a solution for advertisers to adjust the behaviour of Google tags on their website based on the user consent status. 
By pairing the Consent Mode API with the Usercentrics Consent Management Platform (CMP) advertisers can indicate if the user has given consent for cookie usage related to ads and/or advertising.  The supported Google tags will respect this signal and adjust their behaviour accordingly only utilizing cookies if consent was granted for the specific purposes.

!> Google also supports the IAB's TCF framework with its ad systems. Consent Mode is meant to be used by advertisers **not** using a TCF CMP implementation. However, we also recommend implementing the Consent Mode default state into your website if you are using a TCF implementation. This recommendation is due to the fact that the necessary consent signals for ad_user_data and ad_personalization in some Google Tags cannot be guaranteed through the TCF API for now, owing to varying network loading times, and because Google adheres entirely to TCF policies and is registered as a vendor with ID 755 on IAB Global Vendor List, it's mandatory to select it from the Global Vendor List to ensure the accurate signaling of Consent Mode accurately.


Following Google services currently support the Consent Mode:
 
* Conversion Linker (template id: LykAT-gy) -> (Consent type: ad_storage)
* Display & Video 360 (template id: UekC8ye4S) -> (Consent type: ad_storage) 
* Doubleclick Ad (template id: 9V8bg4D63) -> (Consent type: ad_storage) **DEPRECATED PRODUCT**
* DoubleClick Floodlight (template id: ByzZ5EsOsZX) -> (Consent type: ad_storage) **DEPRECATED PRODUCT**
* Google Ads (template id: S1_9Vsuj-Q) -> (Consent type: ad_storage)
* Google Ads Conversion Tracking (template id: twMyStLkn) -> (Consent type: ad_storage)
* Google Ads Remarketing (template id: B1Hk_zoTX) -> (Consent type: ad_storage)
* Google Analytics (template id: HkocEodjb7) -> (Consent type: analytics_storage)
* Google Analytics 4 (template id: 87JYasXPF) -> (Consent type: analytics_storage)
* Google Campaign Manager (template id: pxiRY9112) -> (Consent type: ad_storage) **DEPRECATED PRODUCT**
* Google Campaign Manager 360 (template id: dyHOCwp5Y) -> (Consent type: ad_storage)
* Search Ads 360 (template id: DHS2sEi4b) -> (Consent type: ad_storage)

!> When using the Google Consent Mode, the respective tags (see list above) may **not** be adjusted as described in our [guide](browser-sdk-google-tag-manager-configuration.md). The reason for this is the advantage of the Consent Mode: Google will use the signal to adjust the behaviour of their tags based on the user's consent in the Usercentrics CMP instead of having them blocked when no consent is given.

Details on the tag behaviour with Consent Mode can be found [here](https://support.google.com/analytics/answer/9976101?hl=en).

## Prerequisites

Consent Mode requires that you use gtag.js or Google Tag Manager. If you use older tags versions (like ga.js or analytics.js) you need to update to the latest tag versions first.

## Implementation Example

Implementing the Consent Mode with the Usercentrics CMP solution as alternative to prior blocking requires just 2 steps:

### Step 1: Adjust the existing Google Tag Manager code

Your current Google Tag Manager code may currently look like this:

```
<script type="text/plain" data-usercentrics="Google Tag Manager">
(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,’script','dataLayer','GTM-XXXXXX');</script>

```

This needs to be changed to the following:

!> Please make sure to put these scripts at the very top of the `<head>` and on the same order as below.

> ⚠️ To adjust the default measurement capabilities, set the default values for the command to run on every page of your site before any commands that send measurement data (such as config or event). For more information please check [Adjust Tag Behavior](https://developers.google.com/tag-platform/devguides/consent#adjust_tag_behavior)

```
    <script type="text/javascript">
        // create dataLayer
        window.dataLayer = window.dataLayer || [];
        function gtag() {
            dataLayer.push(arguments);
        }

        // set „denied" as default for both ad and analytics storage, as well as ad_user_data and ad_personalization,
        gtag("consent", "default", {
            ad_user_data: "denied",
            ad_personalization: "denied",
            ad_storage: "denied",
            analytics_storage: "denied",
            wait_for_update: 2000 // milliseconds to wait for update
        });

        // Enable ads data redaction by default [optional]
        gtag("set", "ads_data_redaction", true);
    </script>

    <script type="text/javascript">
        // Google Tag Manager
        (function(w, d, s, l, i) {
            w[l] = w[l] || [];
            w[l].push({
                'gtm.start': new Date().getTime(),
                event: 'gtm.js'
            });
            var f = d.getElementsByTagName(s)[0],
                j = d.createElement(s),
                dl = l != 'dataLayer' ? '&l=' + l : '';
            j.async = true;
            j.src =
                'https://www.googletagmanager.com/gtm.js?id=' + i + dl;
            f.parentNode.insertBefore(j, f);
        })(window, document, 'script', 'dataLayer', 'GTM-XXXXX'); //replace GTM-XXXXXX with Google Tag Manager ID
    </script>
```

**Explanation**

* The first part of the script initializes a plain data layer and provides the gtag function before the gtm.js/gtag.js is loaded
  
```
<script type="text/javascript">
    // create dataLayer
    window.dataLayer = window.dataLayer || [];
    function gtag() {
        dataLayer.push(arguments);
    }
```

* In the middle part of the code the default values for the Consent Mode keys ad_storage, ad_user_data, ad_personalization and analytics_storage are defined. Additionally we set ads_data_redaction to true which means, that ad-click identifiers (e.g., GCLID / DCLID) in consent and conversion pings are redacted and network requests will also be sent through a cookieless domain. This is only in effect, when ad_storage is set to 'denied', if ad_storage is 'granted', ads_data_redaction has no effect. Setting ads_data_redaction to true is optional. More details on behaviour can be found [here](https://support.google.com/analytics/answer/9976101?hl=en).
 
```
   // set „denied" as default for both ad and analytics storage as well as ad_user_data and ad_personalization, 
   gtag("consent", "default", {
       ad_storage: "denied",
       ad_user_data: "denied",
       ad_personalization: "denied", 
       analytics_storage: "denied",
       wait_for_update: 2000 // milliseconds to wait for update
   });

    // Enable ads data redaction by default [optional]
    gtag("set", "ads_data_redaction", true);
    
    </script>    
```

* The last part is the Google Tag Manager script. If you want to use Consent Mode as alternative to prior blocking the type of the script tag is "text/javascript". If you use the [Smart Data Protector](smart-data-protector.md) you may have to exclude Google Tag Manager, Google Analytics and/or Google Ads Remarketing from the blocking by SDP.
 
```
<script type="text/javascript">
    // Google Tag Manager
    (function(w, d, s, l, i) { 
        w[l] = w[l] || [];
        w[l].push({
            'gtm.start': new Date().getTime(),
            event: 'gtm.js'
        });
        var f = d.getElementsByTagName(s)[0],
            j = d.createElement(s),
            dl = l != 'dataLayer' ? '&l=' + l : '';
        j.async = true;
        j.src =
            'https://www.googletagmanager.com/gtm.js?id=' + i + dl;
        f.parentNode.insertBefore(j, f);
    })(window, document, 'script', 'dataLayer', 'GTM-XXXXX'); //replace GTM-XXXXXX with Google Tag Manager ID
</script>
```

### Step 2 (optional): Use the Usercentrics CMP events to signal the consent status via the Consent Mode API for Custom Data Processing Services

!> In case you use custom data processing services, follow the steps below. Otherwise, please jump to step 3.

In order to trigger the Consent Mode API for custom Data Processing Services, you need to first add a window event. On the [Admin Interface](https://admin.usercentrics.eu/) under **Implementation -> Web** please click on **Add new Data Layer** and select **Window Event**. Then expand the **Window Event** card and on the **Window Event Name** field, please insert the name of the event. It can be anything, but just make sure to use the same event name on the script below. Then click on the **+** button and click **Save**.

Assuming you already have a window event in your Usercentrics CMP, add the following script to call the Consent Mode API in order to update the consent mode keys based on the consent status.
In this example we use the event name 'ucEvent' and the 2 custom data processing services 'Google Ads Remarketing' and 'Google Analytics'.
(If you have chosen to use different names for the data processing services use your customized ones instead.)

```
<script type="text/javascript">
// Please replace 'ucEvent' with the event you have just created
window.addEventListener("ucEvent", function (e) {
    if( e.detail && e.detail.event == "consent_status") {
        // Please replace the analytics service name here with the customized service    
        var ucAnalyticsService = 'Google Analytics';
        // Please replace the ad service name here with the customized service
        var ucAdService = 'Google Ads Remarketing';

        if(e.detail.hasOwnProperty(ucAnalyticsService) && e.detail.hasOwnProperty(ucAdService))
        {
            gtag("consent", "update", {
                ad_storage: e.detail[ucAdService] ? 'granted':'denied',
                ad_user_data: e.detail[ucAdService] ? 'granted':'denied',
                ad_personalization: e.detail[ucAdService] ? 'granted':'denied',
                analytics_storage: e.detail[ucAnalyticsService] ? 'granted':'denied'
            });
        }
        else {            
            if(e.detail.hasOwnProperty(ucAdService)) {
                gtag("consent", "update", {
                    ad_storage: e.detail[ucAdService] ? 'granted':'denied',
                    ad_user_data: e.detail[ucAdService] ? 'granted':'denied',
                    ad_personalization: e.detail[ucAdService] ? 'granted':'denied' 
                });
            }            
            if(e.detail.hasOwnProperty(ucAnalyticsService)) {
              gtag("consent", "update", {
                    analytics_storage: e.detail[ucAnalyticsService] ? 'granted':'denied'
                });
            }
        }
    }
});
</script>
```

**Explanation**

* The event is fired on each page load and every time the user actively changes his consent decision.
* Based on the status for both services the Consent Mode API is called to signal the granted or denied state.

### Step 3: Enable Google Consent Mode on the Usercentrics Admin Interface

!> For new customers, Google Consent Mode is enabled by default.

Assuming you've completed the steps above, it's now time to enable the feature.

Go to **Usercentrics Admin Interface -> Configuration -> CMP Settings**, enable Google Consent Mode and click **Save**.

!> Make sure that you only use one of the ways to signal the consent status, meaning either the predefined Data Processing Service templates mentioned [above](consent-mode?id=general-information) or manually linking (custom) Data Processing Service templates to consent mode categories [see step 2](consent-mode?id=step-2-optional-use-the-usercentrics-cmp-events-to-signal-the-consent-status-via-the-consent-mode-api-for-custom-data-processing-services). In case you use Google services as Custom Data Processing Services (DPSs), then we recommend disabling Google Consent Mode option via the Usercentrics Admin Interface and follow the instructions shared [in step 2](consent-mode?id=step-2-optional-use-the-usercentrics-cmp-events-to-signal-the-consent-status-via-the-consent-mode-api-for-custom-data-processing-services) instead.

### Advertiser Consent Mode

!> Advertiser Consent Mode is enabled by default.

When using Consent Mode, it's also possible to manage Advertiser Consent Mode. When enabled, Google will deduce the consent signals for ad_storage, ad_user_data and ad_personalisation from the TC String. It's recommended to enable the Advertiser Consent Mode when enabling Google Consent Mode. To enable/disable the Advertiser Consent Mode go to **Usercentrics Admin Interface → Configuration → CMP Settings**, enable/disable Advertiser Consent Mode and click **Save**.

> [← Back to Feature Overview](browser-cmp?id=features)
