---
source: https://docs.usercentrics.com/#/ab-test
---
# A/B Testing

- [Introduction](ab-test?id=introduction-to-ab-testing)
- [Step 1: Admin Interface Setup](ab-test?id=admin-interface-setup)
  - [Enabling A/B Testing](ab-test?id=enabling-ab-testing-in-admin-interface)
  - [Defining the Variants](ab-test?id=defining-the-variants)
  - [Preview](ab-test?id=preview)
- [Step 2: Performing the A/B Test](ab-test?id=step-2-performing-the-ab-test)
  - [Usercentrics Internal A/B Testing](ab-test?id=usercentrics-internal-ab-testing)
  - [A/B Testing with Third-Party Tool](ab-test?id=ab-testing-with-third-party-tool)
- [Available UI Events](ab-test?id=available-ui-events)
- [Available Properties for Testing](ab-test?id=available-properties-for-ab-testing)

## Introduction to A/B Testing

**A/B testing** refers to a randomized experimentation process that includes two or more versions of a variable (web page, element, etc.), which are shown to different users at different times to determine which version performs best with users and drives the best business metrics.

We now offer A/B Testing functionality via the Usercentrics Consent Management Platform, by enabling changing specific values of the Consent Management Platform, such as color, text, and UI elements. This allows you to identify the best version of your Consent Management Platform to maximize the impact of your website on your business.

In this documentation, we will explain in detail how to enable A/B Testing in the Admin Interface and how to configure the different variants you want to test. We will also show you how to perform A/B Testing with our internal feature or how to set it up in your preferred external A/B testing tools, such as Kameleoon, Trbo or Optimizely. Additionally, we provide recommendations to make your A/B testing simpler and more efficient.

## Step 1: Admin Interface Setup
### Enabling A/B Testing in Admin Interface

The first step to enable A/B Testing is to **enable it in your Admin Interface**. We added the option to enable it via the Implementation tab (in the left menu in the Admin Interface). After opening the Implementation tab, click on **A/B Testing** and you will be presented with the following page.
![](assets/ab-testing/ab1.png)
On this page, click on the toggle to enable A/B Testing and a popup will appear. Please read the information shown there carefully and then click **Enable A/B Testing** to enable the functionality.

### Defining the Variants
After enabling the A/B testing tools you will be shown a page with the Configuration tab. On it, you can add your different **Variants** into JSON. Variants are the different versions of the Consent Management Platform that you want to test against each other on your page. To understand this better, we provide the following example with two different variants, **Variant0** and **Variant1**:
```json
{
  "variant0": {},
  "variant1": {
   "firstLayer": {
     "variant": "BANNER"
   }
  }
 }
```

* **Variant0** shows empty JSON, meaning that it is not modified and represents the Consent Management Platform that you configured inside the Admin Interface. In this case, we assume you have enabled the Privacy Wall in the Admin Interface.
* **Variant1** shows JSON with a rule for the First Layer Layout, determining that in Variant1 the First Layer Layout will be a BANNER instead of a WALL.

You can find all the different [available properties](ab-test?id=available-properties-for-ab-testing.md) for A/B Testing the Usercentrics Consent Management Platform at the end of this documentation.
> Note: For your **TCF 2.2 CMP** only the [specific TCF 2.2 properties](ab-test?id=tcf-20-properties.md) can be used for A/B testing.

### Preview
You can use the preview in the Admin Interface, to compare and test the Variants you defined. To use the preview, please click on the preview button in the bottom left corner of the Admin Interface. Once you decided if the Draft or Live version should be displayed, a drop down menu in the preview allows you to switch between the different variants.

![Preview](/assets/ab-testing/ab2.png)

## Step 2: Performing the A/B Test
After you have set up the A/B test in your admin interface, we offer two ways to deploy the different variants. You can use our internal feature or you can use all common third party A/B testing tools.
![Activating the AB Test](assets/ab-testing/ab_setting.png)
### Usercentrics Internal A/B Testing
You can enable the internal A/B testing by selecting the "Activate with Usercentrics" option in the Admin Interface under Implementation / A/B Testing. When using Usercentrics to display your variants, they are always **evenly distributed** (e.g. 50:50 in case of two variants).

To get insights into the performance of the different variants, different options are provided within the Analytics section in the Admin Interface (for further information in Interaction Analytics see [here](interaction-analytics.md)):
- **Filter** the analytics data for one of the variants under **Interaction Analytics Overview**. To do this, select the respective variant in the "variant" dropdown at the top right corner.
![IA Overview](assets/ab-testing/ab-overview.png)
- Directly **compare** the interaction and accept rates for the variants under **Interaction Analytics Comparison / Variant Analytics**. 
![IA Comparison](assets/ab-testing/ab-comparison.png)
- Download and use the **Interaction Analytics Reporting** to get in-depth insights. An additional "variant" column will tell you which variant was used for each entry in the data.

>⚠️ **Note:** Make sure the toggle "Enable results for AbVariant" is enabled to display the data from your A/B test at the top right corner under Interaction Analytics Overview.

### A/B Testing with Third-Party Tool

To get even more detailed insights into your A/B test, we also offer the possibility to perform it with third-party A/B testing tools. Before diving into the different setups, we would like to give some general technical insights and recommendations.

* **UC_AB_VARIANT**: This variable defines what A/B variant you will be testing on. As this variable will be delivered by the Usercentrics Consent Management Platform to the A/B testing tool to display the different variants, it needs to be set before the Consent Management Platform is loaded.

* **Script Order**: We recommend that you add your **A/B testing tool** script in the `<head>` section of your code before the Usercentrics Consent Management Platform, since the A/B testing tool needs to be loaded before the Consent Management Platform to do the **splitting** properly.

* **UC_UI_CMP_EVENT**: We defined various UI events that can be used in your A/B testing tool to track the interactions of your users with the different variants. For example, these events include ACCEPT_ALL (user clicks on accept all) or SAVE (user clicks on the save button). More information on all available events and how to use them can be found below in the [documentation](ab-test?id=available-ui-events).

Currently, we provide a setup guide for Optimizely and Kameleoon. Soon we will add guides for various other tools, like Google Optimize, Optimizely, and trbo.
> Each of these options will be added as a link to other documentation with the guide for each Tool.

* [**Optimizely**](optimizely-ab-test.md)
* [**Kameleoon**](kameleoon-ab-test.md)
* [**trbo**](trbo-ab-test.md)
* [**Dynamic Yield**](dynamic-yield-ab-test.md)
* [**Google Optimize**](google-optimize-ab-test.md)
    

## Available UI Events
>Note: This section is most likely only relevant for setting up your A/B test with an external tool. No additional events need to be defined when using our predefined UI in combination with the internal A/B testing feature.

As previously described, the **UC_UI_CMP_EVENT** was created to be triggered by the most important actions through the Consent Management Platform, enabling you to listen to user interactions and compare the different variants. 

Within this new **Custom Event**, you can add an object that will specify one **event type** that you need to test. You can do this by using one of these: `{ type: event_type, source: ‘FIRST_LAYER’ | ‘SECOND_LAYER' | 'PRIVACY_BUTTON … }`, where the `event_type` can be:

| Event Name            | Description                                              |
|-----------------------|----------------------------------------------------------|
| CMP_SHOWN             | Event triggered when the CMP is shown                    |
| ACCEPT_ALL            | Event triggered by clicking the Accept All button        |
| DENY_ALL              | Event triggered by clicking the Deny All button          |
| SAVE                  | Event triggered by clicking the Save button              |
| MORE_INFORMATION_LINK | Event triggered by clicking the More Information button or link  |
| IMPRINT_LINK          | Event triggered by clicking the Imprint link             |
| PRIVACY_POLICY_LINK   | Event triggered by clicking the Privacy Policy link      |
| CCPA_TOGGLES_ON       | Event triggered by turning on the CCPA toggle            |
| CCPA_TOGGLES_OFF      | Event triggered by turning off the CCPA toggle           |

    

We’ve created a code example to make these custom events easy to understand and access.
```
window.addEventListener('UC_UI_CMP_EVENT', (data) => {
      console.log(`TEST: source => ${data.detail.source} && type => ${data.detail.type}`) 
});
```
For kameleoon, the Custom event would follow this approach where we listen to the event “a user clicked on the Accept All button” and then forward information to the AB Testing Tool Kameleoon (here called a goal, may differ between tools).
```
//Example for Kameleoon
window.addEventListener('UC_UI_CMP_EVENT', (data) => {
      console.log(`TEST: source => ${data.detail.source} && type => ${data.detail.type} && Variant => ${data.detail.abTestVariant}`) 
      if (data.detail.type === "ACCEPT_ALL") Kameleoon.API.Goals.processConversion(goalID)
});
```
In case you want to test the different user interactions only for a specific layer, you can achieve this by adding an additional condition in the code. Please add `&& data.detail.source == ‘FIRST_LAYER’` after your event type for the first layer or `&& data.detail.source == ‘SECOND_LAYER’` for interactions on the second layer.
```
//Example for Kameleoon
window.addEventListener(‘UC_UI_CMP_EVENT’, (data) => {
      console.log(`TEST: source => ${data.detail.source} && type => ${data.detail.type} && Variant => ${data.detail.abTestVariant}`)
      if (data.detail.type === “ACCEPT_ALL” && data.detail.source == ‘FIRST_LAYER’) Kameleoon.API.Goals.processConversion(goalID)
});
```

## Available Properties for A/B Testing

In the following table, you will have all the available properties that we offer for the A/B testing:

> NOTE: <br> - Every single property is **case sensitive**, so an incorrect input may lead to an unwanted result. <br>- For your **TCF 2.2 CMP** only the [specific TCF 2.2 properties](ab-test?id=tcf-20-properties.md) can be used for A/B testing.<br>- In case you want to add **links** to your variant's banner message property (bannerMessage) on the first layer, see [here](ab-test?id=links-in-banner-message):


### Layout Style

| Label                     | Property                                 | Option                                                                                     | Example                                                                                      |
|---------------------------|------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Background Color  | ```customization.color.layerBackground```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "layerBackground": "0045A5" } } } }```  |
| Text Color  | ```customization.color.text```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "text": "000000" } } } }```  |
| Link Color  | ```customization.color.linkFont```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "linkFont": "000000" } } } }```  |
| Tab Color  | ```customization.color.secondLayerTab```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "secondLayerTab": "FAFAFA" } } } }```  |
| Accent Color  | ```customization.color.tabsBorderColor```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "tabsBorderColor": "0045A5" } } } }```  |


```json
{
 "variant0": {},
 "variant1": {
  "customization": {
    "color": {
      "layerBackground": "0045A5",
      "text": "000000",
      "linkFont": "000000",
      "secondLayerTab": "FAFAFA",
      "tabsBorderColor": "0045A5"
    }
  }
 }
}
```

### First Layer Properties

| Label                    | Property                                  | Option                                                                          | Example                                                                                        |
|--------------------------|-------------------------------------------|---------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| First Layer Layout       | ```firstLayer.variant```                  | ```“WALL”``` (Privacy Wall) OR ```“BANNER”``` (Privacy Banner)                  | ``` {  "variant1": {   "firstLayer": {     "variant": "WALL"   } } ```                 |
| Display Categories       | ```firstLayer.isCategoryTogglesEnabled``` | ```true or false```                                                             | ``` {  "variant1": {   "firstLayer": {     "isCategoryTogglesEnabled": true   } }```   |
| Hide Language Switch     | ```firstLayer.hideLanguageSwitch```       | ```true or false```                                                             | ``` {  "variant1": {   "firstLayer": {     "hideLanguageSwitch": true   }  } } ```     |
| Show "Deny All" Button   | ```firstLayer.hideButtonDeny```           | ```true or false```                                                             | ``` {  "variant1": {   "firstLayer": {     "hideButtonDeny": false   }  } } ```        |
| More Information Trigger | ```firstLayer.secondLayerTrigger```       | ```"LINK"``` (More Information Link) or ```"BUTTON"```(More Information Button) or ```"MORE_LINK_BUTTON"``` (More Information Link in Banner Message) | ``` {  "variant1": {   "firstLayer": {     "secondLayerTrigger": "BUTTON"   }  } } ``` |
| First Layer Background Overlay | ```firstLayer.isOverlayEnabled```       | ```true or false```  | ``` {  "variant1": {   "firstLayer": {     "isOverlayEnabled": true   }  } } ``` |
| First Layer Wall Button Alignment | ```customization.buttonAlignment```       | ```"VERTICAL" or "HORIZONTAL"```  | ``` { "variant1": { "customization": { "buttonAlignment": "HORIZONTAL" } } }``` |
| First Layer Short Message | ```firstLayer.shortMessage``` | Any Text  | ``` {  "variant1": {   "firstLayer": {     "shortMessage": "short message example"  } } ``` |
| Close CMP without accepting | ```firstLayer.closeOption``` | ```"LINK"``` (Close Link) or ```"ICON"```(Close Button)  | ``` {  "variant1": {   "firstLayer": {     "closeOption": "LINK"  } } ``` |


```json
{
 "variant0": {},
 "variant1": {
  "firstLayer": {
    "variant": "WALL",
    "isCategoryTogglesEnabled": true,
    "hideLanguageSwitch": true,
    "hideButtonDeny": false,
    "secondLayerTrigger": "BUTTON",
    "isOverlayEnabled": false,
    "shortMessage": "short message example",
    "closeOption": "LINK"
  },
  "customization": {
    "buttonAlignment": "HORIZONTAL"
  }
 }
} 
```

### First Layer Content Properties


| Label                                               | Property                              | Option              | Example                                                                                                     |
|-----------------------------------------------------|---------------------------------------|---------------------|-------------------------------------------------------------------------------------------------------------|
| First Layer Title                                   | ```labels.firstLayerTitle```          | Any Text            | ```{  "variant1": {  "labels": {     "firstLayerTitle": "First Layer",   }  } }```           |
| Banner Message                                      | ```bannerMessage```                   | Any Text            | ```{  "variant1": {   "bannerMessage": "Banner Message for First Layer<br>"  } }```          |
| Show Short Description on Mobile and Tablet Devices | ```bannerMobileDescriptionIsActive``` | ```true or false``` | ```{  "variant1": {   "bannerMobileDescriptionIsActive": true  } }```                        |
| Short Description                                   | ```bannerMobileDescription```         | Any Text            | ```{  "variant1": {   "bannerMobileDescription": "Short Description for First Layer"  } }``` |
| Read More Label                                     | ```labels.btnBannerReadMore```        | Any Text            | ```{  "variant1": {   "labels": {     "btnBannerReadMore": "Read More"   }  } }```           |
| Imprint URL                                         | ```imprintUrl```                      | Valid URL           | ```{  "variant1": {   "imprintUrl": "www.example.com/imprint"  } }```                        |
| Imprint Link Text                                   | ```labels.imprintLinkText```          | Any Text            | ```{  "variant1": {   "labels": {     "imprintLinkText": "Imprint"   }  } }```               |
| Privacy Policy URL                                  | ```privacyPolicyUrl```                | Valid URL           | ```{  "variant1": {   "privacyPolicyUrl": "www.example.com/privacy-policy"  } }```           |
| Privacy Policy Link Text                            | ```labels.privacyPolicyLinkText```    | Any Text            | ```{  "variant1": {   "labels": {     "privacyPolicyLinkText": "Privacy Policy"   }  } }```  |
| First Layer Use Short Message                       | ```firstLayer.useShortMessage```      | ```true or false``` | ```{ "variant1": {   "firstLayer": {   "useShortMessage": true } }```  |

```json
{
 "variant0": {},
 "variant1": {
  "bannerMessage": "Banner Message for First Layer<br>",
  "bannerMobileDescriptionIsActive": true,
  "bannerMobileDescription": "Short Description for First Layer",
  "imprintUrl": "www.example.com/imprint",
  "privacyPolicyUrl": "www.example.com/privacy-policy",
  "labels": {
    "firstLayerTitle": "First Layer",
    "btnBannerReadMore": "Read More",
    "imprintLinkText": "Imprint",
    "privacyPolicyLinkText": "Privacy Policy"
  },
  "firstLayer": {
    "useShortMessage": true
 }
}
```

### Second Layer Properties


| Label                          | Property                             | Option                                                                           | Example                                                                                    |
|--------------------------------|--------------------------------------|----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Second Layer Layout            | ```secondLayer.variant```            | ```"CENTER"``` (Privacy Settings Center) or ```"SIDE"``` (Privacy Settings Side) | ```{  "variant1": {   "secondLayer": {     "variant": "CENTER"   }  } }```        |
| Privacy Settings Side Position | ```secondLayer.side```               | ```"LEFT"```(Position: Left) or ```"RIGHT"```(Position: Right)                   | ```{  "variant1": {   "secondLayer": {     "side": "LEFT"   }  } }```             |
| Hide Language Switch           | ```secondLayer.hideLanguageSwitch``` | ```true or false```                                                              | ```{  "variant1": {   "secondLayer": {     "hideLanguageSwitch": true   }  } }``` |
| Show ‘Deny All’ Button         | ```secondLayer.hideButtonDeny```     | ```true or false```                                                              | ```{  "variant1": {   "secondLayer": {     "hideButtonDeny": false   }  } }```    |
| Second Layer Background Overlay | ```secondLayer.isOverlayEnabled```       | ```true or false```  | ```{  "variant1": {   "secondLayer": {     "isOverlayEnabled": true   }  } } ``` |
| Second Layer Default View | ```secondLayer.defaultView```       | ```"SRV"``` (Show Service Tab) or ```"CAT"``` (Show Categories Tab)  | ```{  "variant1": {   "secondLayer": {     "defaultView": "CAT"   }  } } ``` |


```json
{
 "variant0": {},
 "variant1": {
  "secondLayer": {
    "variant": "CENTER",
    "side": "LEFT",
    "hideLanguageSwitch": true,
    "hideButtonDeny": false,
    "isOverlayEnabled": true,
    "defaultView": "CAT"
  }
 }
}
```

### Second Layer Content Properties


| Label                    | Property                  | Option   | Example                                                                                                      |
|--------------------------|---------------------------|----------|--------------------------------------------------------------------------------------------------------------|
| Second Layer Title       | ```labels.headerCorner``` | Any Text | ```{  "variant1": {   "labels": {     "headerCorner": "Second Layer Title"   }  } }```              |
| Second Layer Description | ```labels.titleCorner```  | Any Text | ```{  "variant1": {   "labels": {     "titleCorner": "Second Layer Description Message"   }  } }``` |


```json
{
 "variant0": {},
 "variant1": {
  "labels": {
    "headerCorner": "Second Layer Title",
    "titleCorner": "Second Layer Description Message"
  }
 }
}
```

### Privacy Trigger Properties

| Label                          | Property                       | Option               | Example        |
| ------------------------------ | ------------------------------ | -------------------- | -------------- |
| Privacy Button Trigger         | ```privacyButtonIsVisible```   |  ```true or false``` | ```{ "variant1": { "privacyButtonIsVisible": true } }``` |
| Privacy Button Allowed URLs    | ```privacyButtonUrls```        | ```{ "contains": [string] }```       | ```{ "variant1": { "privacyButtonUrls": { "contains": ["/path"] } } }``` |
| Privacy Button Icon            | ```buttonPrivacyOpenIconUrl``` | ```"https://img.usercentrics.eu/misc/icon-fingerprint@2X.png"``` (Fingerprint),  ```"https://img.usercentrics.eu/misc/icon-settings-2X.png"``` (Settings),  ```"https://img.usercentrics.eu/misc/icon-shield-2X.png"``` (Security) or a Valid URL for Custom Icon | ```{ "variant1": { "buttonPrivacyOpenIconUrl": "https://img.usercentrics.eu/misc/icon-fingerprint@2X.png" } }``` |
| Custom Icon for Privacy Button | ```buttonPrivacyOpenIconUrl``` | Valid URL  | ```{ "variant1": {   "buttonPrivacyOpenIconUrl": "https://wwww.example.com/icon.png" } }``` |
| Choose Button Position         | ```buttonDisplayLocation```    | ```"bl"``` (Bottom Left) or ```"br"``` (Bottom Right)   | ```{ "variant1": { "buttonDisplayLocation": "bl" } }```     |
| Background Color         | ```customization.color.privacyButtonBackground```    | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # )   | ```{ "variant1": { "customization": { "color": { "privacyButtonBackground": "0045A5" }} } }```     |


```json
{
 "variant0": {},
 "variant1": {
  "customization": {
    "color": {
      "privacyButtonBackground": "0045A5"
    }
  },
  "privacyButtonIsVisible": true,
  "privacyButtonUrls": {
    "contains": ["/path"]
  },
  "buttonPrivacyOpenIconUrl": "https://img.usercentrics.eu/misc/icon-fingerprint@2X.png",
  "buttonDisplayLocation": "bl"
 }
}
```

### Logo Properties

| Label                       | Property                       | Option                                                                | Example                                                                                                  |
|-----------------------------|--------------------------------|-----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Logo URL                    | ```customization.logoUrl```    | Valid URL                                                             | ```{  "variant1": {   "customization": {     "logoUrl": "https://www.example.com/logo.png"   }  } }``` |
| Logo Position (First Layer) | ```firstLayer.logoPosition```  | ```"LEFT"``` (Left), ```"CENTER"``` (Center) or ```"RIGHT"``` (Right) | ```{  "variant1": {   "firstLayer": {     "logoPosition": "LEFT"   }  } }```                           |
| Logo Alt-Tag                | ```customization.logoAltTag``` | Any Text                                                              | ```{  "variant1": {   "customization": {     "logoAltTag": "Alternative Tag"   }  } }```               |


```json
{
 "variant0": {},
 "variant1": {
  "firstLayer": {
    "logoPosition": "LEFT",
  },
  "customization": {
    "logoUrl": "https://www.example.com/logo.png",
    "logoAltTag": "Alternative Tag"
  }
 }
}
```

### Fonts Properties

| Label                    | Property                        | Option                                                                                                     | Example                                                                                                       |
|--------------------------|---------------------------------|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Font-Family              | ```customization.font.family``` | “System fonts (Default)”, “Helvetica”, “Verdana”, “Georgia”, “Arial” or “Custom font”                      | ```{  "variant1": {   "customization": {     "font": {       "family": "Helvetica"     }   }  } }```       |
| Font-Family Custom Field | ```customization.font.family``` | Any Custom Font that you want to add  (Note: those fonts must be included in your website to make it work) | ```{  "variant1": {   "customization": {     "font": {       "family": "Source Sans Pro"     }   }  } }``` |
| Font-Size                | ```customization.font.size```   | 12, 14, 16, 18                                                                                             | ```{  "variant1": {   "customization": {     "font": {       "size": 16     }   }  } }```                  |


```json
{
 "variant0": {},
 "variant1": {
  "customization": {
    "font": {
      "family": "Helvetica",
      "size": 16
    }
  }
 }
}
```

### Buttons Properties

| Label                      | Property                                      | Option                                                                         | Example                                                                                                             |
|----------------------------|-----------------------------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| Accept Button (Background) | ```customization.color.acceptBtnBackground```       | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "acceptBtnBackground": "FAFAFA"     }   }  } }```       |
| Accept Button (Text)       | ```customization.color.acceptBtnText``` | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "acceptBtnText": "0045A5"     }   }  } }``` |
| Deny Button (Background)   | ```customization.color.denyBtnBackground```   | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "denyBtnBackground": "0045A5"     }   }  } }```   |
| Deny Button (Text)         | ```customization.color.denyBtnText```         | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "denyBtnText": "FAFAFA"     }   }  } }```         |
| Save Button (Background)   | ```customization.color.saveBtnBackground```   | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "saveBtnBackground": "0045A5"     }   }  } }```   |
| Save Button (Text)         | ```customization.color.saveBtnText```         | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "saveBtnText": "FAFAFA"     }   }  } }```         |
| More Button (Background) | ```customization.color.moreBtnBackground```       | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "moreBtnBackground": "FAFAFA"     }   }  } }```       |
| More Button (Text)       | ```customization.color.moreBtnText``` | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "moreBtnText": "0045A5"     }   }  } }``` |
| Rounded corners (px)       | ```customization.borderRadiusButton```        | Enter Any Number                                                               | ```{  "variant1": {   "customization": {     "borderRadiusButton": 4   }  } }```                               |


```json
{
 "variant0": {},
 "variant1": {
  "customization": {
    "borderRadiusButton": 4,
    "color": {
      "acceptBtnBackground": "0045A5",
      "acceptBtnText": "FAFAFA",
      "denyBtnBackground": "0045A5",
      "denyBtnText": "FAFAFA",
      "moreBtnBackground": "0045A5",
      "moreBtnText": "FAFAFA",
      "saveBtnBackground": "0045A5",
      "saveBtnText": "FAFAFA"
    }
  }
 }
}
```

### Toggles Properties


| Label                        | Property                                           | Option                                                                         | Example                                                                                                                    |
|------------------------------|----------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| Active Toggle (Background)   | ```customization.color.toggleActiveBackground```   | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "toggleActiveBackground": "0045A5"     }   }  } }```    |
| Active Toggle (Icon)         | ```customization.color.toggleActiveIcon```         | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "toggleActiveIcon": "FAFAFA",     }   }  } }```         |
| Disabled Toggle (Background) | ```customization.color.toggleDisabledBackground``` | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "toggleDisabledBackground": "FA0000"     }   }  } }```  |
| Disabled Toggle (Icon)       | ```customization.color.toggleDisabledIcon```       | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "toggleDisabledIcon": "FAFAFA"     }   }  } }```        |
| Inactive Toggle (Background) | ```customization.color.toggleInactiveBackground``` | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "toggleInactiveBackground": "989898",     }   }  } }``` |
| Inactive Toggle (Icon)       | ```customization.color.toggleInactiveIcon```       | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{  "variant1": {   "customization": {     "color": {       "toggleInactiveIcon": "FAFAFA"     }   }  } }```        |


```json
{
 "variant0": {},
 "variant1": {
  "customization": {
    "color": {
      "toggleActiveBackground": "0045A5",
      "toggleActiveIcon": "FAFAFA",
      "toggleInactiveBackground": "989898",
      "toggleInactiveIcon": "FAFAFA",
      "toggleDisabledBackground": "FA0000",
      "toggleDisabledIcon": "FAFAFA"
    }
  }
 }
}
```

### TCF 2.2 Properties


| Label                     | Property                                | Option                                                                   | Example                                                                                          |
|---------------------------|-----------------------------------------|--------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| TCF2 Accept All Button Label   | ```tcf2.buttonsAcceptAllLabel```   | Any Text                                                                 | ```{  "variant1": {   "tcf2": {     "buttonsAcceptAllLabel": "Accept All"   }  } }```       |
| TCF2 Deny All Button Label   | ```tcf2.buttonsDenytAllLabel```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "buttonsDenyAllLabel": "Deny All"   }  } }```        |
| TCF2 Save Button Label   | ```tcf2.buttonsSaveLabel```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "buttonsSaveLabel": "Save Settings"   }  } }```        |
| TCF2 Manage Settings Label   | ```tcf2.linksManageSettingsLabel```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "linksManageSettingsLabel": "More Settings"   }  } }```        |
| TCF2 Data Shared Outside Europe Text   | ```tcf2.dataSharedOutsideEUText```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "dataSharedOutsideEUText": "Outside EU Text"   }  } }```         |
| TCF2 First Layer Description   | ```tcf2.firstLayerDescription```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "firstLayerDescription": "description"   }  } }```       |
| TCF2 First Layer Hide Deny Button   | ```tcf2.firstLayerHideButtonDeny```   | ```true or false```                                                                 |  ```{  "variant1": {   "tcf2": {     "firstLayerHideButtonDeny": false   }  } }```        |
| TCF2 First layer Hide Toggles  | ```tcf2.firstLayerHideToggles```   | ```true or false```                                                               |  ```{  "variant1": {   "tcf2": {     "firstLayerHideToggles": false   }  } }```       |
| TCF2 First layer Show Descriptions   | ```tcf2.firstLayerShowDescriptions```   | ```true or false```                                                               |  ```{  "variant1": {   "tcf2": {     "firstLayerShowDescriptions": false   }  } }```       |
| TCF2 First Layer Title  | ```tcf2.firstLayerTitle```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "firstLayerTitle": "Privacy Information"   }  } }```       |
| TCF2 Hide Legitimate Interest Toggles   | ```tcf2.hideLegitimateInterestToggles```   | ```true or false```                                                                |  ```{  "variant1": {   "tcf2": {     "hideLegitimateInterestToggles": false   }  } }```       |
| TCF2 Second Layer Hide Deny Button   | ```tcf2.secondLayerHideButtonDeny```   | ```true or false```                                                                 |  ```{  "variant1": {   "tcf2": {     "secondLayerHideButtonDeny": true   }  } }```      |
| TCF2 Second Layer Hide Toggles   | ```tcf2.secondLayerHideToggles```   | ```true or false```                                                                |  ```{  "variant1": {   "tcf2": {     "secondLayerHideToggles": false   }  } }```        |
| TCF2 Second Layer Title   | ```tcf2.secondLayerTitle```   | Any Text                                                                 |  ```{  "variant1": {   "tcf2": {     "secondLayerTitle": "Privacy Settings"   }  } }```        |
| TCF2 Hide Non IAB Vendors on First Layer   | ```tcf2.hideNonIabOnFirstLayer```   | ```true or false```                                                               |  ```{  "variant1": {   "tcf2": {     "hideNonIabOnFirstLayer": false   }  } }```       |


```json
{
  "variant0": {},
  "variant1": {
    "tcf2": {
      "buttonsAcceptAllLabel": "Accept all",
      "buttonsDenyAllLabel": "Deny all",
      "buttonsSaveLabel": "Save Settings",
      "linksManageSettingsLabel": "More Settings"
      "dataSharedOutsideEUText": "Outside EU Text",
      "firstLayerDescription": "description",
      "firstLayerHideButtonDeny": false,
      "firstLayerHideToggles": false,
      "firstLayerShowDescriptions": false,
      "firstLayerTitle": "Privacy Information",
      "hideLegitimateInterestToggles": false,
      "secondLayerHideButtonDeny": true,
      "secondLayerHideToggles": false,
      "secondLayerTitle": "Privacy Settings",
      "hideNonIabOnFirstLayer": false
    }
  }
}
```

### CCPA First Layer Properties


| Label                     | Property                                | Option                                                                   | Example                                                                                          |
|---------------------------|-----------------------------------------|--------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| CCPA First Layer Layout   | ```ccpa.firstLayerVariant```            | ```"BANNER"``` (CCPA Privacy Banner) or ```"WALL"``` (CCPA Privacy Wall) | ```{  "variant1": {   "ccpa": {     "firstLayerVariant": "BANNER"   }  } }```        |
| CCPA Hide Language Switch | ```ccpa.firstLayerHideLanguageSwitch``` | ```true or false```                                                      | ```{  "variant1": {   "ccpa": {     "firstLayerHideLanguageSwitch": true   }  } }``` |


```json
{
 "variant0": {},
 "variant1": {
  "ccpa": {
    "firstLayerVariant": "BANNER",
    "firstLayerHideLanguageSwitch": true
  }
 }
}
```

### CCPA Second Layer Properties

| Label                     | Property                                 | Option                                                                                     | Example                                                                                      |
|---------------------------|------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| CCPA Second Layer Layout  | ```ccpa.secondLayerVariant```            | ```"CENTER"``` (CCPA Privacy Settings Center) or ```"SIDE"``` (CCPA Privacy Settings Side) | ```{  "variant1": {   "ccpa": {     "secondLayerVariant": "CENTER"   }  } }```  |
| CCPA Hide Language Switch | ```ccpa.secondLayerHideLanguageSwitch``` | ```true or false```                                                                        | {  "variant1": {   "ccpa": {     "secondLayerHideLanguageSwitch": true   }  } } |


```json
{
 "variant0": {},
 "variant1": {
  "ccpa": {
    "secondLayerVariant": "CENTER",
    "secondLayerHideLanguageSwitch": true
  }
 }
}
```

### CCPA Button Properties

| Label                     | Property                                 | Option                                                                                     | Example                                                                                      |
|---------------------------|------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Okay Button CCPA Background Color  | ```customization.color.ccpaButtonColor```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "ccpaButtonColor": "0045A5" } } } }```  |
| Okay Button CCPA Text Color  | ```customization.color.ccpaButtonTextColor```            | Hexadecimal Color Code (On the Admin Interface, For A/B Testing remove the # ) | ```{ "variant1": { "customization": { "color": { "ccpaButtonTextColor": "FFFFFF" } } } }```  |


```json
{
 "variant0": {},
 "variant1": {
  "customization": {
    "color": {
      "ccpaButtonColor": "0045A5",
      "ccpaButtonTextColor": "FFFFFF",
    }
  }
 }
}
```

### Labels Properties

WARNING: These Labels are affected by different translations. This Property table is ENGLISH-only.


| Label              | Property                  | Option   | Example                                                                                                                 |
|--------------------|---------------------------|----------|-------------------------------------------------------------------------------------------------------------------------|
| Button: Save       | ```labels.btnSave```      | Any Text | ```{  "variant0": {},  "variant1": {   "labels": {     "btnSave": "Save Services"   }  } }``` |
| Button: Deny All   | ```labels.btnDeny```      | Any Text | ```{  "variant1": {   "labels": {     "btnDeny": "Deny"     "btnMore": "More",   }  } }```                 |
| Button: More       | ```labels.btnMore```       | Any Text | ```{  "variant1": {   "labels": {     "btnMore": "More"   }  } }```                                        |
| Button: Accept All | ```labels.btnAcceptAll``` | Any Text | ```{  "variant1": {   "labels": {     "btnAcceptAll": "Accept All"   }  } }```                             |
| History (Title)    | ```labels.history```      | Any Text | ```{  "variant1": {   "labels": {     "history": "History"   }  } }```                                     |


```json
{
 "variant0": {},
 "variant1": {
  "labels": {
    "btnSave": "Save Services",
    "btnDeny": "Deny",
    "btnMore": "More",
    "btnAcceptAll": "Accept All",
    "history": "History"
  }
 }
}
```

### Links in Banner Message
In case you want to add **links** to your variant's banner message property on the first layer, you can use the following links:

| Type                     | Link                                 | 
|--------------------|---------------------------|
| Accept All | ```<a href=\"javascript:UC_UI.acceptAllConsents().then(UC_UI.closeCMP);\">Accept all</a>``` |
| Deny All | ```<a href=\"javascript:UC_UI.denyAllConsents().then(UC_UI.closeCMP);\">Deny all</a>``` |
| More Information | ```<a href=\"javascript:UC_UI.showSecondLayer();\">More information</a>``` |
| Regular Link | ```<a href=\"https://www.yourlink.com\">Your text</a>``` |
