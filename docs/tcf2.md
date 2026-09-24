---
source: https://docs.usercentrics.com/#/tcf2
---
> [← Back to Feature Overview](browser-cmp?id=features)
# Usercentrics - TCF 2

## Table of Contents
1. [General Information](tcf2?id=general-information)
2. [TCF 2.0 vs TCF 2.2](tcf2?id=TCF-20-vs-TCF-22)
3. [Configure TCF Framework Settings](tcf2?id=configure-tcf-framework-settings)
4. [Configure the Vendors](tcf2?id=configure-the-vendors)
5. [Configure the Design / Layout](tcf2?id=configure-the-design-layout)
6. [Integrate the TCF CMP into your Website](tcf2?id=integrate-the-tcf-cmp-into-your-website)
7. [Service Integration](tcf2?id=service-integration)

## General Information
The Usercentrics CMP is IAB TCF 2 certified and therefore meets the requirements of the specification of the latest IAB TCF 2.2 framework.

The standard regulates how user consent information has to be captured and used within the programmatic advertising ecosystem, with the goal of a frictionless functioning of the ecosystem through following a standardised approach of implementing data privacy regulations.

We are briefly explaining here some key concepts of the framework:
* The tcString is a technical concept which encodes the consent information in a machine readable format. The tcString contains all required information for any system in the ecosystem to validate what consent has been given by a user and what processing is allowed based on that. The tcString is generated and provided by a CMP at the point where users make consent decisions. Each vendor service that collects data must take the information encoded in the tcString into account and decide if and what consent the user has given.
* The TCF API is the technical concept that allows the vendors integrated in a website or app to interact with the CMP in order to e.g. obtain the tcString in a standardised way. Any IAB TCF 2.2 certified CMP implements this API.
* The Global Vendor List (GVL) is a list of all service vendors who registered for the IAB TCF framework. By registering for the TCF framework these vendors confirm that they comply with the framework policies. The list is maintained by the IAB and regular updates are provided, typically on a weekly basis. The list contains all necessary information about the vendors, that is required by the framework such as descriptions of the services and the data the service uses, the purposes for which the service uses the data, the duration of operation of cookies or similar information that the service stores on a user’s device.
* The TCF framework also prescribes rather strict UX behaviours for CMPs. This impacts the design but also the content shown in a CMP and leaves little room for own optimisation. Aspects like the contrast ratio of colours and the messages that must be shown to users at certain layers of the CMP dialogues are some examples of clearly specified conditions to be met.
* TCF follows the Service-Specific Scope. The Service-Specific Scope means that a user given consent applies only to the specific website/app and cannot be propagated to other sites. This is the scope that is broadly supported within the ad tech ecosystem and the Usercentrics CMP

For further and more detailed reading on the TCF framework, please visit this [page](https://usercentrics.com/knowledge-hub/iab-tcf-2-2-transparency-and-consent-framework-quick-guide/).

> ⚠️ To support TCF , you must be using our CMP v2.

## TCF 2.0 vs TCF 2.2
The IAB released a new TCF version called TCF v2.2 in May 2023 that replaces the existing TCF v2.0 version.

In summary, the TCF v2.2 brings several important changes to the digital advertising industry by expanding the scope of the framework, updating the UI requirements for CMPs, introducing policy changes, enhancing accountability and transparency requirements, and updating technical specifications. These updates are designed to give users more control over their consent choices, increase transparency and accountability, and improve the overall user experience. The IAB has given CMPs and publishers until November 20, 2023 to implement the new policies and specifications.

If your CMP is still running on a TCF 2.0 version, it is important to migrate to the new version until November 20, 2023. If you do not migrate by the required deadline, any TC strings obtained under TCF v2.0 after that date will be considered invalid. This could adversely affect your agreements with advertisers, potentially resulting in a decrease in your ad revenues.

Please visit our [TCF 2.2 migration guide](https://usercentrics.atlassian.net/wiki/spaces/SKB/pages/2668789801) for additonal instructions.

## Configure TCF Framework Settings
To use TCF enable the framework in the Admin Interface under “Configuration” / “Legal Specifications”. Once the framework is activated, you are able to define any settings related to the Framework Configuration, including different Resurfacing options for the UI. Additionally you are able to not disclose purpose 1. 

![Framework Settings](/assets/tcf2/tcf-framesettings.png)

## Configure the Vendors
### Global Vendor List (GVL)
Under “Service Settings” you can configure the vendors or Data Processing Services you are using on you site. The list shown under “Transparency & Consent Framework (TCF)” contains the vendors of the IAB GVL. The CMP automatically manages updates to that list. The list of available vendors is provided by the IAB and vendor information must be used as it is.

By checking the box on the left of each vendor, you can activate the vendor. This means the user will be informed in the CMP banners that you are using this vendor on your site. You can deactivate the vendor at any time by unchecking the same checkbox.

**Data Transfer Outside of EU/EEA**: Inside each vendor, you can specify if the vendor is transferring data outside the EU / EEA”. The information will be displayed inside the respective vendor on the second layer of the CMP. 

![Vendorlist](/assets/tcf2/tcf-vendorlist.png)

### Stacks
Stacks are combinations of Purposes and/or Special Features of processing personal data used by the participants in the Framework. These stacks may be used to substitute Initial Layer information about two or more Purposes and/or Special Features. An important note is that Purposes must not be included in more than one stack and must not be presented as part of a Stack and outside of Stacks at the same time. Conversely, any stacks used must not include the same Purpose more than once, nor Purposes should be presented separately from stacks.

![Stacks](/assets/tcf2/tcf-stacks.png)

### Publisher Restrictions
TCF allows you to signal restrictions on how the vendors may process personal data. It is possible to either restrict the purposes for which personal data is processed or specify the legal basis for vendors that signaled flexibility on the legal basis in the Global Vendor List.

Once a purpose has been restricted in the Admin Interface, a vendor must respect the restriction signal that disallows the processing for the specific purpose regardless of whether or not they have declared that purpose to be flexible.

**Behaviour for flexible purposes**: In the case that a vendor declared a purpose with a default legal basis (consent or legitimate interest) but also declared this purpose as flexible, the legal basis restriction must be respected if set in the Admin Interface. That means for example if a vendor declared a purpose as legitimate interest but also declared that purpose to be flexible and the legal basis was restricted to consent, the vendor must check for the consent signal and must not apply the legitimate interest signal.

**Disable legitimate interest**: In case you want to disable legitimate interest for your TCF CMP, you need to restrict all purposes to consent in the Admin Interface.

![Stacks](/assets/tcf2/tcf-prestrictions.png)

### Non-IAB Vendors

The configuration of non-IAB vendors works the usual way. You may define the service categories and choose from our list of fully prepared services or define your own custom services. There is nothing TCF specific to consider with the configuration of the non-IAB services.

![NonIabVendors](/assets/tcf2/tcf-noniabvendor.png)


## Configure the Design / Layout
### The CMP UI

The first layer contains all the textual information required by the framework, as well as the list of purposes that apply to the vendors you configured. The user may choose on this first layer to opt in/out of certain purposes or go to the second layer to view more details.

![Firstlayer](/assets/tcf2/tcf-ui-firstlayer.png)

The second layer gives users details about the enabled vendors and purposes. On this layer the user has more detailed opt in/out options.

![Secondlayer](/assets/tcf2/tcf-ui-secondlayer.png)

You also have the choice to decide how you want to give your users access to their current Privacy Settings - via Privacy Button or Privacy Link. 

The CMP UI is customisable and you may choose for example your background color, an overlay, fonts, headlines, logo and individual toggle and button colours


### Layout

Under the “Appearance” → “Layout” section you may choose your layout options.

In the "Layout" section you are able to edit what options do you want to show in each layer and also how you want to show Privacy Trigger.

For the First Layer, you are able to display the following options:

![Appearance](/assets/tcf2/tcf-firstlayer.png)

* Show Descriptions for Purposes and Stacks,
* Show the "Deny All" Button,
* Show Non-IAB Purposes,
* Show Toggles in First Layer, and
* Show information on data transfer outside of the EU / EEA

For the Second Layer, you can choose to display the "Deny All" button:

![Appearance](/assets/tcf2/tcf-secondlayer.png)

For the Privacy Trigger, we offer two choices for the layout:

![Appearance](/assets/tcf2/tcf-privacytrigger.png)

* Privacy Button, where you can choose to render it on Bottom Left or Bottom Right
* Privacy Link

### Styling

Under the “Appearance” → “Styling” section you may choose your styling options.

In the Styling section, you are presented with a significant number of options to stylize your CMP:

* **Layout**: You can decide to choose the colors of your CMP (Background, Text, Tabs and Links). Further you can define how round the corners of the CMP will be and activate a background shadow and overlay.
* **Logo**: You can display your own logo in the CMP,
* **Font**: You can decide if you want to use one of the system defined fonts in the CMP or if you want to use a custom font for the CMP.
* **Buttons**: You can define the colors of each different button and the corner radius.
* **Toggles**: You can define the colors for each state of a toggle, whether is active, inactive or disabled.
* **Privacy Trigger**: You can define style changes to the icon, where you can choose one of our system defined icons or if you want to use a custom icon. Additionally it is possible to set the color and size of the button for both desktop and mobile.


### Content

The CMP also offers customisation options under the “Content” section. Some texts must remain as provided due to TCF requirements and can not be edited.

![Appearance](/assets/tcf2/tcf-content.png)

Following are the customisable elements:

* Layer titels
* Additional banner messages
* Data transfer outside of the EU / EEA message
* Resurface Description for Web
* Resurface Description for App
* Imprint link URL
* Privacy policy URL
* Privacy policy link text
* Button labels

## Integrate the TCF CMP into your Website
Once all the above is configured, the last step required to integrate the CMP in your site, is to place the TCF related CMP Script tag in your website:

    <script id="usercentrics-cmp" data-settings-id="XXXXXXXX" src="https://app.usercentrics.eu/browser-ui/latest/loader.js" data-tcf-enabled></script>

## Service Integration
Here a few important notes on what you need to consider when integration vendors / services under TCF.


### IAB Vendors
The Usercentrics CMP exposes the IAB TCF API which offers a standardised way for publishers and vendors to fetch the users consent information. IAB certified vendors are able to work with the TCF API, which means in most cases no further modification or adjustments to vendor scripts are needed. 

In order to react on changes of the consent state vendors can make use of the event listener provided by the API using the __tcfapi function with the command addEventListener. Sample:

```
__tcfapi('addEventListener', 2, function(tcData,success){
    // inital tc string information
    if(success && tcData.eventStatus === 'tcloaded') {
        console.log(' TCF tcLoaded Event - tcString: '+tcData.tcString);        
    }
    // tc string after user interaction completed
    else if(success && tcData.eventStatus === 'useractioncomplete') {
        console.log('TCF useractioncomplete Event - tcString: '+tcData.tcString);
    }
    else {
        // do something else
    }
});
```

The eventStatus property of the TCData object shall be one of the following:

| eventStatus          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ```tcloaded```           | This shall be the value for the  eventStatus  property of the  [TCData](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#tcdata) object when a CMP is loaded and is prepared to surface a TC String to any calling scripts on the page. A CMP is only prepared to surface a TC String for this  eventStatus  if an existing, valid TC String is available to the CMP and it is not intending to surface the UI. If, however, the CMP will surface the UI because of an invalid TC String (e.g. it is too old, incorrect or does not reflect all the information the CMP needs to gather from the user) then an event with this  eventStatus  must not be triggered |
| ```cmpuishown```         | This shall be the value for the  eventStatus  property of the  [TCData](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#tcdata) object any time the UI is surfaced or re-surfaced, a TC String is available and has rendered "Transparency" in accordance with the  [TCF Policy](https://iabeurope.eu/iab-europe-transparency-consent-framework-policies/) . The CMP shall create a TC string with all the surfaced vendors’ legitimate interest signals set to true and all the consent signals set to false. If previous TC signals are present a CMP may also merge those into the now-available TC String in accordance with the policy.                                                                                                  |
| ```useractioncomplete``` | This shall be the value for the  eventStatus  property of the  [TCData](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#tcdata) object whenever a user has confirmed or re-confirmed their choices in accordance with  [TCF Policy](https://iabeurope.eu/iab-europe-transparency-consent-framework-policies/)  and a CMP is prepared to respond to any calling scripts with the corresponding TC String.                                                                                                                                                                                                                                                                                                                                      |



#### Depricated "GetTC Data" command
In the TCF Version 2.0 it was also possible to request the consent state when the CMP is loaded using the __tcfapi function with the command getTCData. This command is not available anymore in TCF 2.2.

More details about the TCF CMP API and its commands can be found [here](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#cmp-api-v20).

### Non-IAB Vendors
Non-IAB vendors do not follow the framework’s concepts and therefore do not use the TCF API or tcString. For those, there is nothing TCF specific to be considered. Use our [Direct Integration Guide](https://usercentrics.com/knowledge-hub/direct-integration-usercentrics-script-website/) to handle non-IAB vendors. For further information how to adjust non-IAB services, see [here](#niab)

### Tag Management Systems
If you are using a tag manager for vendor script integration, you should consider the following information.

IAB registered vendors have to be loaded in order to request the tcString from our CMP and understand the information. Therefore, no tag manager triggers should be configured around those vendor tags.

Non-IAB vendors need to be handled manually. The CMP version 2 uses a an event “consent_status” which should be used in your tag manager to trigger the services. 
Here's an example configuration for the [Google Tag Manager](browser-sdk-google-tag-manager-configuration.md)


> [← Back to Feature Overview](browser-cmp?id=features)
