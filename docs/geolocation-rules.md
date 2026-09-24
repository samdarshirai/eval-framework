---
source: https://docs.usercentrics.com/#/geolocation-rules
---
# User Guide to Geolocation Rules

-   [Introduction](geolocation-rules?id=introduction)
    -   [Prerequisites](geolocation-rules?id=prerequisites)
-   [Overview](geolocation-rules?id=overview)
-   [Create Rulesets](geolocation-rules?id=create-rulesets)
-   [Constraints](geolocation-rules?id=constraints)

## Introduction

The Usercentrics Interface allows to setup Geolocation Rules for all your Configurations (Setting-IDs). This will enable you to setup and display different CMP Configurations on your website based on the user’s location.

### Prerequisites

- You must already have an account setup with Usercentrics. This includes the setup of a Company and the assignment of Configurations (Setting-IDs) to the Company in the interface. For more information, see all required steps [here](account-interface.md).
- **Regional Settings:** Within your configurations, **no** regional settings must be in place when using Geolocation Rules. This means set the regional settings in the Admin Interface / Configuration / Legal Specifications to "Display CMP to all users (default)" and for CCPA configurations to "Display CCPA CMP to all users".

## Overview

The overview page lists all the Companies and Unassigned Configurations.

![Geolocation Rules 1](/assets/geolocation-rules/geolocation-rules1.png)
  
Go to the Company for which you want to create rulesets for by clicking on it in the overview. Notice that the Configurations (Setting-IDs) belonging to that company are listed under “Configurations”.

![Geolocation Rules 2](/assets/geolocation-rules/geolocation-rules2.png)

## Create Rulesets

Go to “Gelolocations Rulesets” and click on “Create Ruleset”. A pop-up wizard will appear which will guide you through the necessary steps:

![Geolocation Rules 3](/assets/geolocation-rules/geolocation-rules3.png)

1. Fill out the required details for your Ruleset

![Geolocation Rules 4](/assets/geolocation-rules/geolocation-rules4.png)

2. Select the Configuration (Setting-ID) to be displayed in the global rule.

**Note:** This is a mandatory step in creating a ruleset because this acts as a fallback rule when no other rule is specified for that region. In this step you can decide whether to display the banner or not. Example: You can later have a rule for Germany, Belgium, Netherlands, another rule for United States while for the rest of the world you can decide whether you want to display the global configuration or hide the banner for the users. In any case, a global configuration needs to be defined in order to collect consents.

![Geolocation Rules 5](/assets/geolocation-rules/geolocation-rules5.png)

Once a Ruleset is created, the page would look as below:

![Geolocation Rules 6](/assets/geolocation-rules/geolocation-rules6.png)

3. Setup regional rules. Click on the 3 dots menu in the created ruleset and select “Regional Settings”.

![Geolocation Rules 7](/assets/geolocation-rules/geolocation-rules7.png)

A pop-up wizard will appear which will allow you to add regional settings. For each rule, fill in the necessary details:

-   Rule Name
    
-   The region you want to display the rule in. Here you can directly search for regions in the dropdown field
    
-   The Configuration (Setting-ID) that should be displayed for that region  

![Geolocation Rules 8](/assets/geolocation-rules/geolocation-rules8.png)

![Geolocation Rules 9](/assets/geolocation-rules/geolocation-rules9.png)  

Similarly add more rules, as per requirement and “Save Changes”.

![Geolocation Rules 10](/assets/geolocation-rules/geolocation-rules10.png)

![Geolocation Rules 11](/assets/geolocation-rules/geolocation-rules11.png)

4. The Ruleset Details, Global Rule, and Regional Rules can be edited from the same 3 dots menu on top.

![Geolocation Rules 12](/assets/geolocation-rules/geolocation-rules12.png)

5. Finally, copy the script tag from the “Implementation” tab to your website and the banner will take care of the rest!

If you have an existing Usercentrics script tag, please replace it with this newly generated script tag containing the `data-ruleset-id` attribute.

![Geolocation Rules 13](/assets/geolocation-rules/geolocation-rules13.png)

Similarly, you can add more rulesets for different domains using the “Add Ruleset” button.

The “Configuration” page will provide an overview of all your configuration and rulesets.

## Constraints

-   Configurations belonging to a ruleset cannot be unassigned from the company or permanently deleted
-   Companies having rulesets cannot be deleted. You need to delete the ruleset first
-   Configurations supporting TCF 2.2 should belong to a single ruleset and need to be implemented with the respective TCF Script Tag. Example - Do not combine GDPR and TCF configuration in one rule. It will not always work as expected **(not applicable to In-App SDK)**