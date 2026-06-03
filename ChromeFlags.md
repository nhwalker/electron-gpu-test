# Chrome flags — Chromium 146.0.7680.166 (`chrome://flags` dump)

Machine-extracted dump of the `chrome://flags` entry table for the Chromium build that ships in **Electron 41.1.1** (Chromium **146.0.7680.166**).

> **Why this is a separate file:** Electron has no `about_flags` UI, so `chrome://flags` does not open in Electron (see [`Flags.md`](./Flags.md) §7). These entries come from the **upstream Chrome layer** of the same Chromium version — they document the togglable features/switches the underlying Chromium understands. In Electron you reach them via `--enable-features` / command-line switches (the **Backing** column), not the flags page.

## Source & method

Generated deterministically (parsed, not hand-written) from two files at the exact release tag:

* [`chrome/browser/about_flags.cc`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/chrome/browser/about_flags.cc) — the `kFeatureEntries[]` table (flag id, platforms, backing feature/switch).
* [`chrome/browser/flag_descriptions.h`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/chrome/browser/flag_descriptions.h) — the `inline constexpr` title/description strings (M146 inlined the old `.cc` into this header).

**Counts:** 1442 total `chrome://flags` entries in this build; **682** are available on Linux desktop and are listed below (the rest are Android / ChromeOS / Windows / macOS-only).

**Caveats:**

* The table also `#include`s build-time generated entries via `chrome/browser/unexpire_flags_gen.inc` (flag-unexpiry); those are **not** captured here.
* The **Backing** column is what you actually pass to Electron: `--enable-features=<Feature>` for `FEATURE_VALUE_TYPE`, or the named `--switch` for `SINGLE_VALUE_TYPE`.
* Descriptions live in `flag_descriptions.h` (`k<Name>Description`); only the **Title** (`chrome://flags` display name) is reproduced here, for width.

## Reading the Backing column

| Macro | Meaning |
|---|---|
| `FEATURE_VALUE_TYPE(f)` | Toggles `base::Feature f` → `--enable-features=f` / `--disable-features=f` |
| `FEATURE_WITH_PARAMS_VALUE_TYPE(f,…)` | Same, with preset field-trial parameters |
| `SINGLE_VALUE_TYPE(s)` | Adds command-line switch `s` |
| `SINGLE_DISABLE_VALUE_TYPE(s)` | Adds a *disabling* switch `s` |
| `ORIGIN_LIST_VALUE_TYPE(s,…)` | Switch `s` taking an origin list |
| `MULTI_VALUE_TYPE(choices)` | Multiple-choice flag (each choice sets its own switch) |

## Linux-available flags (682)

| Flag id | Title | Backing feature / switch | Platforms |
|---|---|---|---|
| `?` | Browsing History Actor Integration M1 | `FEATURE_VALUE_TYPE(history::kBrowsingHistoryActorIntegrationM1)` | Desktop |
| `?` | Browsing History Actor Integration M2 | `FEATURE_VALUE_TYPE(history::kBrowsingHistoryActorIntegrationM2)` | Desktop |
| `?` | Browsing History Actor Integration M3 | `FEATURE_VALUE_TYPE(history::kBrowsingHistoryActorIntegrationM3)` | Desktop |
| `accessible-pdf-form` | Accessible PDF Forms | `FEATURE_VALUE_TYPE(chrome_pdf::features::kAccessiblePDFForm)` | Desktop |
| `account-storage-prefs-themes-search-engines` | Account storage of preferences, themes and search engines | `MULTI_VALUE_TYPE(kAccountStoragePrefsThemesAndSearchEnginesChoices)` | Desktop |
| `ack-copy-output-request-early-for-view-transition` | Ack CopyOutputRequest early for View Transition | `FEATURE_VALUE_TYPE(features::kAckCopyOutputRequestEarlyForViewTransition)` | All |
| `ai-mode-omnibox-entry-point` | AI Mode Omnibox entrypoint | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kAiModeOmniboxEntryPoint, kOmniboxAiModeEntryPointVariations, "OmniboxAiModeEntryPointVariations")` | Desktop |
| `aim-entry-point-direct-navigation` | AI Mode Omnibox Entrypoint always navigates | `FEATURE_VALUE_TYPE(omnibox::kAiModeEntryPointAlwaysNavigates)` | Desktop |
| `aim-server-eligibility` | AIM Server Eligibility | `FEATURE_VALUE_TYPE(omnibox::kAimServerEligibilityEnabled)` | All |
| `aim-server-eligibility-include-client-locale` | AIM Server Eligibility: Include Client Locale | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kAimServerEligibilityIncludeClientLocale, kAimServerEligibilityIncludeClientLocaleVariations, "AimServerEligibilityIncludeClientLocale")` | All |
| `aim-use-pec-api` | Use PEC API | `FEATURE_VALUE_TYPE(omnibox::kAimUsePecApi)` | All |
| `align-pdf-default-print-settings-with-html` | Align PDF default print settings with HTML | `FEATURE_VALUE_TYPE(printing::features::kAlignPdfDefaultPrintSettingsWithHTML)` | All |
| `align-wakeups` | Align delayed wake ups at 125 Hz | `FEATURE_VALUE_TYPE(base::kAlignWakeUps)` | All |
| `allow-all-sites-to-initiate-mirroring` | Allow all sites to initiate mirroring | `FEATURE_VALUE_TYPE(media_router::kAllowAllSitesToInitiateMirroring)` | Desktop |
| `allow-insecure-localhost` | Allow invalid certificates for resources loaded from localhost. | `SINGLE_VALUE_TYPE(switches::kAllowInsecureLocalhost)` | All |
| `apply-clientside-model-predictions-for-password-types` | Apply clientside model predictions for password forms. | `FEATURE_VALUE_TYPE(password_manager::features:: kApplyClientsideModelPredictionsForPasswordTypes)` | All |
| `ash-debug-shortcuts` | Debugging keyboard shortcuts | `SINGLE_VALUE_TYPE(ash::switches::kAshDebugShortcuts)` | All |
| `audio-ducking` | Audio Ducking | `FEATURE_WITH_PARAMS_VALUE_TYPE(media::kAudioDucking, kAudioDuckingAttenuationVariations, "AudioDucking")` | Desktop |
| `auto-picture-in-picture-for-video-playback` | Auto picture in picture for video playback | `FEATURE_VALUE_TYPE(media::kAutoPictureInPictureForVideoPlayback)` | Desktop |
| `autofill-ai-based-amount-extraction-ignore-seen-terms-for-testing` |  | `FEATURE_VALUE_TYPE(autofill::features:: kAutofillAiBasedAmountExtractionIgnoreSeenTermsForTesting)` | Desktop |
| `autofill-disable-bnpl-country-check-for-testing` | Disable the country check for BNPL testing | `FEATURE_VALUE_TYPE(autofill::features::kAutofillDisableBnplCountryCheckForTesting)` | Desktop, Android |
| `autofill-enable-ai-based-amount-extraction` | Enable AI-based checkout amount extraction on Chrome | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableAiBasedAmountExtraction)` | Desktop |
| `autofill-enable-amount-extraction` | Enable checkout amount extraction. | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableAmountExtraction)` | Desktop, Android |
| `autofill-enable-amount-extraction-testing` | Enable amount extraction testing | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableAmountExtractionTesting)` | Desktop, Android |
| `autofill-enable-buy-now-pay-later` | Enable buy now pay later on Autofill | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableBuyNowPayLater)` | Desktop, Android |
| `autofill-enable-buy-now-pay-later-for-externally-linked` | Enable buy now pay later for externally linked BNPL issuer | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableBuyNowPayLaterForExternallyLinked)` | Desktop, Android |
| `autofill-enable-buy-now-pay-later-for-klarna` | Enable buy now pay later on Autofill for Klarna | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableBuyNowPayLaterForKlarna)` | Desktop, Android |
| `autofill-enable-buy-now-pay-later-syncing` | Enable syncing buy now pay later user data. | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableBuyNowPayLaterSyncing)` | Desktop, Android |
| `autofill-enable-buy-now-pay-later-updated-suggestion-second-line-string` |  | `FEATURE_VALUE_TYPE(autofill::features:: kAutofillEnableBuyNowPayLaterUpdatedSuggestionSecondLineString)` | All |
| `autofill-enable-card-benefits-for-american-express` | Enable showing card benefits for American Express cards | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableCardBenefitsForAmericanExpress)` | All |
| `autofill-enable-card-benefits-for-bmo` | Enable showing card benefits for BMO cards | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableCardBenefitsForBmo)` | All |
| `autofill-enable-card-benefits-sync` | Enable syncing card benefits | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableCardBenefitsSync)` | All |
| `autofill-enable-card-info-runtime-retrieval` | Enable retrieval of card info(with CVC) from issuer for enrolled cards | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableCardInfoRuntimeRetrieval)` | All |
| `autofill-enable-cvc-storage-and-filling` | Enable CVC storage and filling for payments autofill | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableCvcStorageAndFilling)` | All |
| `autofill-enable-cvc-storage-and-filling-enhancement` | Enable CVC storage and filling enhancement for payments autofill | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableCvcStorageAndFillingEnhancement)` | All |
| `autofill-enable-cvc-storage-and-filling-standalone-form-enhancement` |  | `FEATURE_VALUE_TYPE(autofill::features:: kAutofillEnableCvcStorageAndFillingStandaloneFormEnhancement)` | All |
| `autofill-enable-downstream-card-awareness-iph` | Enable showing in-product help UI for downstream card awareness | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableDownstreamCardAwarenessIph)` | Desktop |
| `autofill-enable-flat-rate-card-benefits-from-curinos` | Enable showing flat rate card benefits sourced from Curinos | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableFlatRateCardBenefitsFromCurinos)` | All |
| `autofill-enable-fpan-risk-based-authentication` | Enable risk-based authentication for FPAN retrieval | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableFpanRiskBasedAuthentication)` | All |
| `autofill-enable-non-affiliated-loyalty-cards` | Enable filling on non-affiliated loyalty cards | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableNonAffiliatedLoyaltyCardsFilling)` | Desktop, Android |
| `autofill-enable-pay-now-pay-later-tabs` | Enable Pay Now Pay Later tabs UI for payments autofill on Chrome | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnablePayNowPayLaterTabs)` | Desktop |
| `autofill-enable-prefetching-risk-data-for-retrieval` | Enable prefetching of risk data during payments autofill retrieval | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnablePrefetchingRiskDataForRetrieval)` | All |
| `autofill-enable-save-and-fill` | Enable Save and Fill | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableSaveAndFill)` | Desktop |
| `autofill-enable-support-for-home-and-work` | Support for Home and Work addresses in Autofill | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableSupportForHomeAndWork)` | All |
| `autofill-enable-support-for-name-and-email-profile` | Support for name and email addresses in Autofill | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableSupportForNameAndEmail)` | All |
| `autofill-enable-vcn-3ds-authentication` | Enable 3DS authentication for virtual cards | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableVcn3dsAuthentication)` | Desktop |
| `autofill-enable-wallet-branding` | Update Google Pay branding to Wallet where applicable | `FEATURE_VALUE_TYPE(autofill::features::kAutofillEnableWalletBranding)` | All |
| `autofill-manual-testing-data` | Autofill manual testing data | `STRING_VALUE_TYPE(autofill::kManualContentImportForTestingFlag, "")` | All |
| `autofill-more-prominent-popup` | More prominent Autofill popup | `FEATURE_VALUE_TYPE(autofill::features::kAutofillMoreProminentPopup)` | Desktop |
| `autofill-payments-field-swapping` | Swap credit card suggestions | `FEATURE_VALUE_TYPE(autofill::features::kAutofillPaymentsFieldSwapping)` | All |
| `autofill-prefer-buy-now-pay-later-blocklists` | Prefer blocklists instead of allowlists for Payments Autofill Buy Now Pay Later (BNPL) | `FEATURE_VALUE_TYPE(autofill::features::kAutofillPreferBuyNowPayLaterBlocklists)` | All |
| `autofill-shared-storage-server-card-data` | Enable storing autofill server card data in the shared storage database | `FEATURE_VALUE_TYPE(autofill::features::kAutofillSharedStorageServerCardData)` | All |
| `autofill-show-bubbles-based-on-priorities` | Show bubbles based on priorities | `FEATURE_VALUE_TYPE(autofill::features::kAutofillShowBubblesBasedOnPriorities)` | All |
| `autofill-unmask-card-request-timeout` | Timeout for the credit card unmask request | `FEATURE_VALUE_TYPE(autofill::features::kAutofillUnmaskCardRequestTimeout)` | All |
| `autofill-vcn-enroll-strike-expiry-time` | Expiry duration for VCN enrollment strikes | `FEATURE_WITH_PARAMS_VALUE_TYPE(autofill::features::kAutofillVcnEnrollStrikeExpiryTime, kAutofillVcnEnrollStrikeExpiryTimeOptions, "AutofillVcnEnrollStrikeExpiryTime")` | All |
| `automatic-usb-detach` | Automatically detach USB kernel drivers | `FEATURE_VALUE_TYPE(features::kAutomaticUsbDetach)` | Android, Linux |
| `backdrop-filter-mirror-edge` | Backdrop Filter Mirror Edge | `FEATURE_VALUE_TYPE(features::kBackdropFilterMirrorEdgeMode)` | All |
| `background-resource-fetch` | Background Resource Fetch | `FEATURE_VALUE_TYPE(blink::features::kBackgroundResourceFetch)` | All |
| `bind-cookies-to-port` | Bind cookies to their setting origin's port | `FEATURE_VALUE_TYPE(net::features::kEnablePortBoundCookies)` | All |
| `bind-cookies-to-scheme` | Bind cookies to their setting origin's scheme | `FEATURE_VALUE_TYPE(net::features::kEnableSchemeBoundCookies)` | All |
| `block-cross-partition-blob-url-fetching` | Block Cross Partition Blob URL Fetching | `FEATURE_VALUE_TYPE(features::kBlockCrossPartitionBlobUrlFetching)` | All |
| `block-v8-optimizer-on-unfamiliar-sites` | Automatic JS Optimizer Control | `FEATURE_VALUE_TYPE(content_settings::features:: kBlockV8OptimizerOnUnfamiliarSitesSetting)` | Desktop |
| `bookmark-tab-group-conversion` | Bookmark and tab group conversion | `FEATURE_VALUE_TYPE(features::kBookmarkTabGroupConversion)` | Desktop |
| `bookmarks-tree-view` | Top Chrome Bookmarks Tree View | `FEATURE_VALUE_TYPE(features::kBookmarksTreeView)` | Desktop |
| `boundary-event-dispatch-tracks-node-removal` | Boundary Event Dispatch Tracks Node Removal | `FEATURE_VALUE_TYPE(blink::features::kBoundaryEventDispatchTracksNodeRemoval)` | All |
| `browser-initiated-automatic-picture-in-picture` | Browser initiated automatic picture in picture | `FEATURE_VALUE_TYPE(blink::features::kBrowserInitiatedAutomaticPictureInPicture)` | Desktop |
| `browsing-history-similar-visits-grouping` | Browsing History Grouping Improvements | `FEATURE_VALUE_TYPE(history::kBrowsingHistorySimilarVisitsGrouping)` | Desktop |
| `bundled-security-settings` | Bundled Security Settings | `FEATURE_VALUE_TYPE(safe_browsing::kBundledSecuritySettings)` | Desktop |
| `bundled-security-settings-secure-dns-v2` | Bundled Security Settings Secure Dns V2 | `FEATURE_VALUE_TYPE(safe_browsing::kBundledSecuritySettingsSecureDnsV2)` | Desktop |
| `by-date-history-in-side-panel` | By Date History in Side Panel | `FEATURE_VALUE_TYPE(features::kByDateHistoryInSidePanel)` | Desktop |
| `canvas-2d-hibernation` | Hibernation for 2D canvas | `FEATURE_VALUE_TYPE(blink::features::kCanvas2DHibernation)` | All |
| `canvas-2d-layers` | Enables canvas 2D methods BeginLayer and EndLayer | `SINGLE_VALUE_TYPE(switches::kEnableCanvas2DLayers)` | All |
| `canvas-draw-element` | HTML-in-Canvas | `FEATURE_VALUE_TYPE(blink::features::kCanvasDrawElement)` | All |
| `captured-surface-control` | Captured Surface Control | `FEATURE_VALUE_TYPE(blink::features::kCapturedSurfaceControl)` | Desktop |
| `cast-message-logging` | Enables logging of all Cast messages. | `FEATURE_VALUE_TYPE(media_router::kCastMessageLogging)` | Desktop |
| `cast-mirroring-target-playout-delay` | Changes the target playout delay for Cast mirroring. | `MULTI_VALUE_TYPE(kCastMirroringTargetPlayoutDelayChoices)` | Desktop |
| `cast-streaming-hardware-h264` | Toggle hardware accelerated H.264 video encoding for Cast Streaming | `ENABLE_DISABLE_VALUE_TYPE(switches::kCastStreamingForceEnableHardwareH264, switches::kCastStreamingForceDisableHardwareH264)` | Desktop |
| `cast-streaming-hardware-hevc` | Toggle hardware accelerated HEVC video encoding for Cast Streaming | `FEATURE_VALUE_TYPE(media::kCastStreamingHardwareHevc)` | Desktop |
| `cast-streaming-hardware-vp8` | Toggle hardware accelerated VP8 video encoding for Cast Streaming | `ENABLE_DISABLE_VALUE_TYPE(switches::kCastStreamingForceEnableHardwareVp8, switches::kCastStreamingForceDisableHardwareVp8)` | Desktop |
| `cast-streaming-hardware-vp9` | Toggle hardware accelerated VP9 video encoding for Cast Streaming | `ENABLE_DISABLE_VALUE_TYPE(switches::kCastStreamingForceEnableHardwareVp9, switches::kCastStreamingForceDisableHardwareVp9)` | Desktop |
| `cast-streaming-media-video-encoder` | Toggles using the media::VideoEncoder implementation for Cast Streaming | `FEATURE_VALUE_TYPE(media::kCastStreamingMediaVideoEncoder)` | Desktop |
| `cast-streaming-performance-overlay` | Toggle a performance metrics overlay while Cast Streaming | `FEATURE_VALUE_TYPE(media::kCastStreamingPerformanceOverlay)` | Desktop |
| `chrome-web-store-navigation-throttle` | Chrome Web Store navigation throttle | `FEATURE_VALUE_TYPE(enterprise::webstore::kChromeWebStoreNavigationThrottle)` | Linux, Mac, Win |
| `chrome-wide-echo-cancellation` | Chrome-wide echo cancellation | `FEATURE_VALUE_TYPE(media::kChromeWideEchoCancellation)` | Mac, Win, Linux |
| `click-to-call` | Click-To-Call | `FEATURE_VALUE_TYPE(kClickToCall)` | All |
| `collaboration-entreprise-v2` | Collaboration Entreprise V2 | `FEATURE_VALUE_TYPE(data_sharing::features::kCollaborationEntrepriseV2)` | All |
| `collaboration-messaging` | Collaboration Messaging | `FEATURE_VALUE_TYPE(collaboration::features::kCollaborationMessaging)` | All |
| `collaboration-shared-tab-group-account-data` | Shared Tab Group messaging sync | `FEATURE_VALUE_TYPE(syncer::kSyncSharedTabGroupAccountData)` | All |
| `compose-selection-nudge` | Compose Selection Nudge | `FEATURE_WITH_PARAMS_VALUE_TYPE(compose::features::kEnableComposeSelectionNudge, kComposeSelectionNudgeVariations, "ComposeSelectionNudge")` | Win, Linux, Mac, CrOS |
| `composebox-uses-chrome-compose-client` | Composebox uses chrome-compose client | `FEATURE_VALUE_TYPE(omnibox::kComposeboxUsesChromeComposeClient)` | Desktop, Android |
| `connection-allowlists` | Connection Allowlists | `FEATURE_VALUE_TYPE(network::features::kConnectionAllowlists)` | All |
| `contextual-cueing` | Contextual cueing | `FEATURE_WITH_PARAMS_VALUE_TYPE(contextual_cueing::kContextualCueing, kContextualCueingEnabledOptions, "ContextualCueingEnabledOptions")` | Desktop |
| `contextual-search-box-uses-contextual-search-provider` | Contextual search box uses contextual search provider | `FEATURE_VALUE_TYPE(omnibox_feature_configs::ContextualSearch:: kContextualSearchBoxUsesContextualSearchProvider)` | Desktop |
| `contextual-search-open-lens-action-uses-thumbnail` | Contextual search open Lens action uses thumbnail | `FEATURE_VALUE_TYPE(omnibox_feature_configs::ContextualSearch:: kContextualSearchOpenLensActionUsesThumbnail)` | Desktop |
| `contextual-suggestion-ui-improvements` | Contextual suggestions UI improvements | `MULTI_VALUE_TYPE(kContextualSuggestionsUiImprovementsChoices)` | Desktop |
| `contextual-suggestions-ablate-others-when-present` | Contextual suggestions ablate others when present | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox_feature_configs::ContextualSearch:: kContextualSuggestionsAblateOthersWhenPresent, kContextualSuggestionsAblateOthersWhenPresentVariations, "ContextualSuggestionsAblateOthersWhenPresent")` | Desktop |
| `contextual-tasks` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(contextual_tasks::kContextualTasks, kContextualTasksVariations, "ContextualTasks")` | Desktop |
| `contextual-tasks-context` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(contextual_tasks::kContextualTasksContext, kContextualTaskContextVariations, "ContextualTasks")` | Desktop |
| `contextual-tasks-context-library` |  | `FEATURE_VALUE_TYPE(contextual_tasks::kContextualTasksContextLibrary)` | Desktop |
| `contextual-tasks-expand-button` |  | `FEATURE_VALUE_TYPE(contextual_tasks::kContextualTasksExpandButton)` | Desktop |
| `contextual-tasks-suggestions-enabled` |  | `FEATURE_VALUE_TYPE(contextual_tasks::kContextualTasksSuggestionsEnabled)` | Desktop |
| `controlled-frame-web-request-security-info` | Enable SecurityInfo in WebRequest API for ControlledFrame | `FEATURE_VALUE_TYPE(blink::features::kControlledFrameWebRequestSecurityInfo)` | Desktop |
| `create-new-tab-group-app-menu-top-level` | Create new tab group menu option at the top level of the app menu | `FEATURE_VALUE_TYPE(features::kCreateNewTabGroupAppMenuTopLevel)` | Desktop |
| `credential-management-unified-ui` | Credential Management Unified UI | `FEATURE_VALUE_TYPE(password_manager::features::kCredentialManagementUnifiedUi)` | Desktop |
| `cros-block-warnings` | Chrome OS block warnings | `FEATURE_VALUE_TYPE(content_settings::features:: kCrosSystemLevelPermissionBlockedWarnings)` | Desktop |
| `cryptography-compliance-cnsa` | Cryptography Compliance (CNSA) | `FEATURE_VALUE_TYPE(features::kCryptographyComplianceCnsa)` | All |
| `css-gamut-mapping` | CSS Gamut Mapping | `FEATURE_VALUE_TYPE(blink::features::kBakedGamutMapping)` | All |
| `css-grid-lanes-layout` | CSS Grid Lanes Layout | `FEATURE_VALUE_TYPE(blink::features::kCSSGridLanesLayout)` | All |
| `cups-ipp-printing-backend` | CUPS IPP Printing Backend | `FEATURE_VALUE_TYPE(printing::features::kCupsIppPrintingBackend)` | Desktop |
| `customize-chrome-side-panel-extensions-card` | Customize Chrome Side Panel Extension Card | `FEATURE_VALUE_TYPE(ntp_features::kCustomizeChromeSidePanelExtensionsCard)` | Desktop |
| `customize-chrome-wallpaper-search` | Customize Chrome Wallpaper Search | `FEATURE_VALUE_TYPE(ntp_features::kCustomizeChromeWallpaperSearch)` | Desktop |
| `customize-chrome-wallpaper-search-button` | Customize Chrome Wallpaper Search Button | `FEATURE_VALUE_TYPE(ntp_features::kCustomizeChromeWallpaperSearchButton)` | Desktop |
| `customize-chrome-wallpaper-search-inspiration-card` | Customize Chrome Wallpaper Search Inspiration Card | `FEATURE_VALUE_TYPE(ntp_features::kCustomizeChromeWallpaperSearchInspirationCard)` | Desktop |
| `customize-tab-group-color-palette` | Customize tab group color palette | `FEATURE_VALUE_TYPE(features::kCustomizeTabGroupColorPalette)` | Desktop |
| `cws-info-fast-check` | CWS Info Fast Check | `FEATURE_VALUE_TYPE(extensions::kCWSInfoFastCheck)` | Desktop |
| `cws-promotion-banner-flag` | Enable Chrome Web Store Promotion Banner | `FEATURE_VALUE_TYPE(extensions_features::kEnableShouldShowPromotion)` | Desktop |
| `data-sharing` | Data Sharing | `FEATURE_WITH_PARAMS_VALUE_TYPE(data_sharing::features::kDataSharingFeature, kDatasharingVariations, "Enabled")` | All |
| `data-sharing-debug-logs` | Enable data sharing debug logs | `SINGLE_VALUE_TYPE(data_sharing::kDataSharingDebugLoggingEnabled)` | All |
| `data-sharing-join-only` | Data Sharing Join Only | `FEATURE_VALUE_TYPE(data_sharing::features::kDataSharingJoinOnly)` | All |
| `data-sharing-non-production-environment` | Data Sharing server environment | `FEATURE_VALUE_TYPE(data_sharing::features::kDataSharingNonProductionEnvironment)` | All |
| `dbd-revamp-desktop` | Revamped Delete Browsing Data dialog | `FEATURE_VALUE_TYPE(browsing_data::features::kDbdRevampDesktop)` | Desktop |
| `default-angle-vulkan` | Default ANGLE Vulkan | `FEATURE_VALUE_TYPE(features::kDefaultANGLEVulkan)` | Linux, Android |
| `default-browser-changed-os-notification` | Default Browser Changed OS Notification | `FEATURE_VALUE_TYPE(default_browser::kDefaultBrowserChangedOsNotification)` | Desktop |
| `default-browser-framework` | Default Browser Framework | `FEATURE_VALUE_TYPE(default_browser::kDefaultBrowserFramework)` | Desktop |
| `default-search-engine-prewarm` | Default search engine prewarm | `FEATURE_VALUE_TYPE(features::kPrewarm)` | Desktop |
| `default-site-instance-groups` | Default SiteInstanceGroups | `FEATURE_VALUE_TYPE(features::kDefaultSiteInstanceGroups)` | All |
| `deprecate-unload` | Deprecate the unload event | `FEATURE_VALUE_TYPE(network::features::kDeprecateUnload)` | All |
| `device-posture` | Device Posture API | `FEATURE_VALUE_TYPE(blink::features::kDevicePosture)` | All |
| `devtools-individual-request-throttling` | Enable individual request throttling in DevTools | `FEATURE_VALUE_TYPE(features::kDevToolsIndividualRequestThrottling)` | All |
| `devtools-live-edit` | Enable JavaScript live editing in DevTools | `FEATURE_VALUE_TYPE(features::kDevToolsLiveEdit)` | All |
| `devtools-privacy-ui` | DevTools Privacy UI | `FEATURE_VALUE_TYPE(features::kDevToolsPrivacyUI)` | All |
| `devtools-project-settings` | DevTools Project Settings | `FEATURE_VALUE_TYPE(features::kDevToolsWellKnown)` | Desktop |
| `devtools-protocol-monitor` | Enable protocol monitor in DevTools | `FEATURE_VALUE_TYPE(features::kDevToolsProtocolMonitor)` | All |
| `direct-sockets-in-service-workers` | Direct Sockets API in Service Workers | `FEATURE_VALUE_TYPE(blink::features::kDirectSocketsInServiceWorkers)` | Desktop |
| `direct-sockets-in-shared-workers` | Direct Sockets API in Shared Workers | `FEATURE_VALUE_TYPE(blink::features::kDirectSocketsInSharedWorkers)` | Desktop |
| `disable-accelerated-2d-canvas` | Accelerated 2D canvas | `SINGLE_DISABLE_VALUE_TYPE(switches::kDisableAccelerated2dCanvas)` | All |
| `disable-accelerated-video-decode` | Hardware-accelerated video decode | `SINGLE_DISABLE_VALUE_TYPE(switches::kDisableAcceleratedVideoDecode)` | Mac, Win, CrOS, Android, Linux |
| `disable-autofill-strike-system` | Disable the Autofill strike system | `FEATURE_VALUE_TYPE(strike_database::features::kDisableStrikeSystem)` | All |
| `disable-javascript-harmony-shipping` | Latest stable JavaScript features | `SINGLE_DISABLE_VALUE_TYPE(switches::kDisableJavaScriptHarmonyShipping)` | All |
| `disable-process-reuse` |  | `FEATURE_VALUE_TYPE(features::kDisableProcessReuse)` | Desktop |
| `disallow-doc-written-script-loads` | Block scripts loaded via document.write | `—` | All |
| `discount-autofill` |  | `FEATURE_VALUE_TYPE(commerce::kDiscountAutofill)` | Desktop |
| `discount-on-navigation` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(commerce::kEnableDiscountInfoApi, kDiscountsVariations, "DisocuntOnNavigation")` | Desktop |
| `document-patching` | Document patching | `FEATURE_VALUE_TYPE(blink::features::kDocumentPatching)` | All |
| `document-picture-in-picture-animate-resize` | Document Picture-in-Picture Animate Resize | `FEATURE_VALUE_TYPE(media::kDocumentPictureInPictureAnimateResize)` | Desktop |
| `drop-input-events-while-paint-holding` | Drop input events while paint-holding is active | `FEATURE_VALUE_TYPE(blink::features::kDropInputEventsWhilePaintHolding)` | All |
| `dse-preload2` | Default Search Engine preload 2 | `FEATURE_VALUE_TYPE(features::kDsePreload2)` | All |
| `dse-preload2-on-press` | Default Search Engine preload 2, on-press triggers | `FEATURE_VALUE_TYPE(features::kDsePreload2OnPress)` | All |
| `element-capture-cross-tab` | Element Capture cross-tab | `FEATURE_VALUE_TYPE(features::kElementCaptureOfOtherTabs)` | Desktop |
| `email-verification-protocol` | Email Verification Protocol | `FEATURE_VALUE_TYPE(features::kEmailVerificationProtocol)` | All |
| `enable-accessibility-on-screen-mode` | On-Screen Only Accessibility Nodes | `FEATURE_VALUE_TYPE(::features::kAccessibilityOnScreenMode)` | All |
| `enable-auto-disable-accessibility` | Auto-disable Accessibility | `FEATURE_VALUE_TYPE(features::kAutoDisableAccessibility)` | All |
| `enable-autofill-credit-card-upload` | Enable offering upload of Autofilled credit cards | `FEATURE_VALUE_TYPE(autofill::features::kAutofillUpstream)` | All |
| `enable-ax-tree-fixing` | AXTree Fixing | `FEATURE_VALUE_TYPE(features::kAXTreeFixing)` | Desktop |
| `enable-benchmarking` | Enable benchmarking | `MULTI_VALUE_TYPE(kEnableBenchmarkingChoices)` | All |
| `enable-bound-session-credentials` | Device Bound Session Credentials | `FEATURE_VALUE_TYPE(switches::kEnableBoundSessionCredentials)` | Mac, Linux |
| `enable-bound-session-credentials-software-keys-for-manual-testing` |  | `FEATURE_VALUE_TYPE(unexportable_keys:: kEnableBoundSessionCredentialsSoftwareKeysForManualTesting)` | Mac, Win, Linux |
| `enable-cast-streaming-av1` | Enable AV1 video encoding for Cast Streaming | `FEATURE_VALUE_TYPE(media::kCastStreamingAv1)` | Desktop |
| `enable-cast-streaming-vp8` | Enable VP8 video encoding for Cast Streaming | `FEATURE_VALUE_TYPE(media::kCastStreamingVp8)` | Desktop |
| `enable-cast-streaming-vp9` | Enable VP9 video encoding for Cast Streaming | `FEATURE_VALUE_TYPE(media::kCastStreamingVp9)` | Desktop |
| `enable-cast-streaming-with-hidpi` | HiDPI tab capture support for Cast Streaming | `FEATURE_VALUE_TYPE(mirroring::features::kCastEnableStreamingWithHiDPI)` | Desktop |
| `enable-chrome-refresh-token-binding` | Chrome Refresh Token Binding | `FEATURE_VALUE_TYPE(switches::kEnableChromeRefreshTokenBinding)` | Mac, Linux |
| `enable-clipboardchange-event` | ClipboardChangeEvent | `FEATURE_VALUE_TYPE(blink::features::kClipboardChangeEvent)` | All |
| `enable-compression-dictionary-transport` | Compression dictionary transport | `FEATURE_VALUE_TYPE(network::features::kCompressionDictionaryTransport)` | All |
| `enable-compression-dictionary-ttl` | Compression dictionary transport ttl | `FEATURE_VALUE_TYPE(network::features::kCompressionDictionaryTTL)` | All |
| `enable-controlled-frame` | Enable Controlled Frame | `FEATURE_VALUE_TYPE(blink::features::kControlledFrame)` | Desktop |
| `enable-cros-touch-text-editing-redesign` | Touch Text Editing Redesign | `FEATURE_VALUE_TYPE(features::kTouchTextEditingRedesign)` | All |
| `enable-cross-device-pref-tracker` | Enable Cross-Device Pref Tracker | `FEATURE_VALUE_TYPE(sync_preferences::features::kEnableCrossDevicePrefTracker)` | All |
| `enable-debug-for-store-billing` | Web Payments App Store Billing Debug Mode | `FEATURE_VALUE_TYPE(payments::features::kAppStoreBillingDebug)` | All |
| `enable-delegated-compositing` | Enable delegated compositing | `FEATURE_VALUE_TYPE(features::kDelegatedCompositing)` | All |
| `enable-desktop-pwas-additional-windowing-controls` | Desktop PWA Additional Windowing Controls | `FEATURE_VALUE_TYPE(blink::features::kDesktopPWAsAdditionalWindowingControls)` | Desktop |
| `enable-desktop-pwas-app-title` | Desktop PWA Application Title | `FEATURE_VALUE_TYPE(blink::features::kWebAppEnableAppTitle)` | Desktop |
| `enable-desktop-pwas-borderless` | Desktop PWA Borderless | `FEATURE_VALUE_TYPE(blink::features::kWebAppBorderless)` | Desktop |
| `enable-desktop-pwas-elided-extensions-menu` | Desktop PWAs elided extensions menu | `FEATURE_VALUE_TYPE(features::kDesktopPWAsElidedExtensionsMenu)` | Desktop |
| `enable-desktop-pwas-sub-apps` | Sub Apps for Isolated Web Apps | `FEATURE_VALUE_TYPE(blink::features::kSubApps)` | Desktop |
| `enable-desktop-pwas-tab-strip` | Desktop PWA tab strips | `FEATURE_VALUE_TYPE(blink::features::kDesktopPWAsTabStrip)` | Desktop |
| `enable-desktop-pwas-tab-strip-customizations` | Desktop PWA tab strip customizations | `FEATURE_VALUE_TYPE(blink::features::kDesktopPWAsTabStripCustomizations)` | Desktop |
| `enable-desktop-pwas-tab-strip-settings` | Desktop PWA tab strips settings | `FEATURE_VALUE_TYPE(features::kDesktopPWAsTabStripSettings)` | Desktop |
| `enable-devtools-deep-link-via-extensibility-api` | Extensibility API support for deep-links within DevTools | `FEATURE_VALUE_TYPE(blink::features::kEnableDevtoolsDeepLinkViaExtensibilityApi)` | Desktop |
| `enable-drdc` | Enables Display Compositor to use a new gpu thread. | `FEATURE_VALUE_TYPE(features::kEnableDrDc)` | All |
| `enable-enterprise-badging-for-ntp-footer` | Enable enterprise badging on the New Tab Page | `FEATURE_VALUE_TYPE(features::kEnterpriseBadgingForNtpFooter)` | Mac, Win, Linux |
| `enable-experimental-accessibility-language-detection` | Experimental accessibility language detection | `SINGLE_VALUE_TYPE(::switches::kEnableExperimentalAccessibilityLanguageDetection)` | All |
| `enable-experimental-accessibility-language-detection-dynamic` | Experimental accessibility language detection for dynamic content | `SINGLE_VALUE_TYPE(::switches::kEnableExperimentalAccessibilityLanguageDetectionDynamic)` | All |
| `enable-experimental-cookie-features` | Enable experimental cookie features | `MULTI_VALUE_TYPE(kEnableExperimentalCookieFeaturesChoices)` | All |
| `enable-experimental-web-platform-features` | Experimental Web Platform features | `SINGLE_VALUE_TYPE(switches::kEnableExperimentalWebPlatformFeatures)` | All |
| `enable-experimental-webassembly-features` | Experimental WebAssembly | `SINGLE_VALUE_TYPE(switches::kEnableExperimentalWebAssemblyFeatures)` | All |
| `enable-experimental-webassembly-shared-everything` | Experimental WebAssembly Shared-Everything Threads | `FEATURE_VALUE_TYPE(features::kEnableExperimentalWebAssemblySharedEverything)` | All |
| `enable-experimental-webassembly-stack-switching` | WebAssembly stack switching | `FEATURE_VALUE_TYPE(features::kWebAssemblyStackSwitching)` | All |
| `enable-extension-install-policy-fetching` | Enable Extension Install Policy Fetching | `FEATURE_VALUE_TYPE(policy::features::kEnableExtensionInstallPolicyFetching)` | Win, Mac, Linux, CrOS |
| `enable-fenced-frames-developer-mode` | Enable the `FencedFrameConfig` constructor. | `FEATURE_VALUE_TYPE(blink::features::kFencedFramesDefaultMode)` | All |
| `enable-force-dark` | Auto Dark Mode for Web Contents | `FEATURE_VALUE_TYPE(blink::features::kForceWebContentsDarkMode)` | All |
| `enable-force-download-to-onedrive` | Enable forced download to OneDrive | `FEATURE_VALUE_TYPE(enterprise_data_protection::kEnableForceDownloadToOneDrive)` | Desktop |
| `enable-future-v8-vm-features` | Future V8 VM features | `FEATURE_VALUE_TYPE(features::kV8VmFuture)` | All |
| `enable-generic-oidc-auth-profile-management` | Enable generic OIDC profile management | `FEATURE_VALUE_TYPE(profile_management::features:: kEnableGenericOidcAuthProfileManagement)` | Linux, Mac, Win |
| `enable-global-vaapi-lock` | Global lock on the VA-API wrapper. | `FEATURE_VALUE_TYPE(media::kGlobalVaapiLock)` | CrOS, Linux |
| `enable-gpu-rasterization` | GPU rasterization | `MULTI_VALUE_TYPE(kEnableGpuRasterizationChoices)` | All |
| `enable-gpu-service-logging` | Enable gpu service logging | `SINGLE_VALUE_TYPE(switches::kEnableGPUServiceLogging)` | All |
| `enable-headless-live-caption` | Headless Live Captions | `FEATURE_VALUE_TYPE(media::kHeadlessLiveCaption)` | Desktop |
| `enable-headless-live-caption-early-start` | Headless Caption Early Start | `FEATURE_VALUE_TYPE(media::kHeadlessCaptionEarlyStart)` | Desktop |
| `enable-isolated-sandboxed-iframes` | Isolated sandboxed iframes | `FEATURE_WITH_PARAMS_VALUE_TYPE(blink::features::kIsolateSandboxedIframes, kIsolateSandboxedIframesGroupingVariations, "IsolateSandboxedIframes" /* trial name */)` | All |
| `enable-isolated-web-app-allowlist` | Enable an allowlist for Isolated Web Apps | `FEATURE_VALUE_TYPE(features::kIsolatedWebAppManagedAllowlist)` | Desktop |
| `enable-isolated-web-app-dev-mode` | Enable Isolated Web App Developer Mode | `FEATURE_VALUE_TYPE(features::kIsolatedWebAppDevMode)` | Desktop |
| `enable-isolated-web-apps` | Enable Isolated Web Apps | `FEATURE_VALUE_TYPE(features::kIsolatedWebApps)` | Desktop |
| `enable-iwa-key-distribution-component` | Enable the Iwa Key Distribution component | `FEATURE_VALUE_TYPE(component_updater::kIwaKeyDistributionComponent)` | Desktop |
| `enable-javascript-harmony` | Experimental JavaScript | `SINGLE_VALUE_TYPE(switches::kJavaScriptHarmony)` | All |
| `enable-jxl-image-format` | Enable JXL image format | `FEATURE_VALUE_TYPE(blink::features::kJXLImageFormat)` | All |
| `enable-lazy-load-image-for-invisible-pages` | Enable lazy load image for invisible page | `FEATURE_WITH_PARAMS_VALUE_TYPE(blink::features::kEnableLazyLoadImageForInvisiblePage, kSearchSuggsetionPrerenderTypeVariations, "EnableLazyLoadImageForInvisiblePage")` | All |
| `enable-lens-overlay` | Lens overlay | `FEATURE_WITH_PARAMS_VALUE_TYPE(lens::features::kLensOverlay, kLensOverlayVariations, "LensOverlay")` | Desktop |
| `enable-lens-overlay-edu-action-chip` | Lens Overlay EDU action chip | `FEATURE_WITH_PARAMS_VALUE_TYPE(lens::features::kLensOverlayEduActionChip, kLensOverlayEduActionChipVariations, "LensOverlayEduActionChip")` | Desktop |
| `enable-lens-overlay-entrypoint-label-alt` | Lens overlay entrypoint label | `FEATURE_WITH_PARAMS_VALUE_TYPE(lens::features::kLensOverlayEntrypointLabelAlt, kLensOverlayEntrypointLabelAltVariations, "LensOverlayEntrypointLabelAltVariations")` | Desktop |
| `enable-lens-overlay-force-empty-csb-query` | Lens overlay force empty CSB query | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayForceEmptyCsbQuery)` | Desktop |
| `enable-lens-overlay-image-context-menu-actions` | Lens overlay image context menu actions | `FEATURE_WITH_PARAMS_VALUE_TYPE(lens::features::kLensOverlayImageContextMenuActions, kLensOverlayImageContextMenuActionsVariations, "LensOverlayImageContextMenuActions")` | Desktop |
| `enable-lens-overlay-latency-optimizations` | Lens overlay latency optimizations | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayLatencyOptimizations)` | Desktop |
| `enable-lens-overlay-side-panel-open-in-new-tab` | Lens overlay side panel open in new tab | `FEATURE_VALUE_TYPE(lens::features::kLensOverlaySidePanelOpenInNewTab)` | Desktop |
| `enable-lens-overlay-straight-to-srp` | Lens overlay straight to SRP | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayStraightToSrp)` | Desktop |
| `enable-lens-overlay-text-selection-context-menu-entrypoint` | Lens overlay text selection context menu entrypoint | `FEATURE_WITH_PARAMS_VALUE_TYPE(lens::features::kLensOverlayTextSelectionContextMenuEntrypoint, kLensOverlayTextSelectionContextMenuEntrypointVariations, "LensOverlayTextSelectionContextMenuEntrypoint")` | Desktop |
| `enable-lens-overlay-translate-button` | Lens overlay translate button | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayTranslateButton)` | Desktop |
| `enable-lens-overlay-translate-languages` | More Lens overlay translate languages | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayTranslateLanguages)` | Desktop |
| `enable-lens-overlay-updated-visuals` | Lens overlay updated visuals | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayVisualSelectionUpdates)` | Desktop |
| `enable-lens-search-aim-m3` | Enables AIM in Lens side panel. | `FEATURE_VALUE_TYPE(lens::features::kLensSearchAimM3)` | Desktop |
| `enable-lens-search-side-panel-new-feedback` | Lens side panel new feedback | `FEATURE_VALUE_TYPE(lens::features::kLensSearchSidePanelNewFeedback)` | Desktop |
| `enable-lens-standalone` | Enable Lens features in Chrome. | `FEATURE_VALUE_TYPE(lens::features::kLensStandalone)` | Desktop |
| `enable-media-link-helpers` | Media Link Helpers | `FEATURE_VALUE_TYPE(media::kMediaLinkHelpers)` | Desktop |
| `enable-network-logging-to-file` | Enable network logging to file | `SINGLE_VALUE_TYPE(network::switches::kLogNetLog)` | All |
| `enable-network-service-sandbox` | Enable the network service sandbox. | `FEATURE_VALUE_TYPE(sandbox::policy::features::kNetworkServiceSandbox)` | Linux, CrOS |
| `enable-ntp-browser-promos` | Enable new tab page browser feature suggestions | `FEATURE_WITH_PARAMS_VALUE_TYPE(user_education::features::kEnableNtpBrowserPromos, kEnableNtpBrowserPromosVariations, "EnableNtpBrowserPromos")` | Desktop |
| `enable-ntp-enterprise-shortcuts` | Enables enterprise shortcuts for the New Tab Page | `FEATURE_VALUE_TYPE(ntp_tiles::kNtpEnterpriseShortcuts)` | CrOS, Linux, Mac, Win |
| `enable-oauth-multilogin-cookies-binding` | Enable OAuthMultilogin Cookies Binding | `FEATURE_VALUE_TYPE(switches::kEnableOAuthMultiloginCookiesBinding)` | Mac, Win, Linux |
| `enable-oauth-multilogin-cookies-binding-server-experiment` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(switches::kEnableOAuthMultiloginCookiesBindingServerExperiment, kOAuthMultiloginCookieBindingEnforcementVariations, "EnableOAuthMultiloginCookiesBindingServerExperiment")` | Mac, Win, Linux |
| `enable-oop-print-drivers` | Enables Out-of-Process Printer Drivers | `FEATURE_VALUE_TYPE(printing::features::kEnableOopPrintDrivers)` | Desktop |
| `enable-parallel-downloading` | Parallel downloading | `FEATURE_VALUE_TYPE(download::features::kParallelDownloading)` | All |
| `enable-pixel-canvas-recording` | Enable pixel canvas recording | `FEATURE_VALUE_TYPE(features::kEnablePixelCanvasRecording)` | Desktop |
| `enable-preferences-account-storage` | Enable the account data storage for preferences for syncing users | `FEATURE_VALUE_TYPE(switches::kEnablePreferencesAccountStorage)` | Desktop |
| `enable-process-per-site-up-to-main-frame-threshold` | Enable ProcessPerSite up to main frame threshold | `FEATURE_VALUE_TYPE(features::kProcessPerSiteUpToMainFrameThreshold)` | Desktop, Android |
| `enable-quic` | Experimental QUIC protocol | `ENABLE_DISABLE_VALUE_TYPE(switches::kEnableQuic, switches::kDisableQuic)` | All |
| `enable-resampling-scroll-events-experimental-prediction` | Enable experimental prediction for scroll events | `FEATURE_WITH_PARAMS_VALUE_TYPE(::features::kResamplingScrollEventsExperimentalPrediction, kResamplingScrollEventsExperimentalPredictionVariations, "ResamplingScrollEventsExperimentalLatency")` | All |
| `enable-secure-payment-confirmation-fallback-ux` | Secure Payment Confirmation Fallback UX | `FEATURE_VALUE_TYPE(payments::features::kSecurePaymentConfirmationFallback)` | All |
| `enable-segmentation-internals-survey` | Segmentation survey internals page and model | `FEATURE_VALUE_TYPE(segmentation_platform::features::kSegmentationSurveyPage)` | All |
| `enable-show-autofill-signatures` | Show autofill signatures. | `SINGLE_VALUE_TYPE(autofill::switches::kShowAutofillSignatures)` | All |
| `enable-site-search-allow-user-override-policy` | Enable allow_user_override field for SiteSearchSettings policy | `FEATURE_VALUE_TYPE(omnibox::kEnableSiteSearchAllowUserOverridePolicy)` | CrOS, Linux, Mac, Win |
| `enable-standard-device-bound-session-credentials` | Device Bound Session Credentials (Standard) | `FEATURE_WITH_PARAMS_VALUE_TYPE(net::features::kDeviceBoundSessions, kStandardBoundSessionCredentialsVariations, "standard-device-bound-sessions")` | Mac, Win, Linux |
| `enable-standard-device-bound-session-credentials-federated-sessions` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(net::features::kDeviceBoundSessionsFederatedRegistration, kStandardBoundSessionCredentialsFederatedSessionsVariations, "standard-device-bound-sessions-federated-sessions")` | Mac, Win, Linux |
| `enable-standard-device-bound-session-devtools-debugging` | Device Bound Session Credentials (Standard) DevTools Debugging | `FEATURE_VALUE_TYPE(features::kDeviceBoundSessionsDevTools)` | Mac, Win, Linux |
| `enable-standard-device-bound-session-google` | Device Bound Session Credentials (Standard) on Google | `FEATURE_VALUE_TYPE(net::features::kDeviceBoundSessionsForRestrictedSites)` | Mac, Win, Linux |
| `enable-standard-device-bound-session-google-experiment-id` | Experiment ID for Device Bound Session Credentials (Standard) on Google | `FEATURE_WITH_PARAMS_VALUE_TYPE(net::features::kDeviceBoundSessionsForRestrictedSitesExperimentId, kDeviceBoundSessionsForRestrictedSitesExperimentIdVariations, "DeviceBoundSessionsForRestrictedSitesExperimentIdVariations")` | Mac, Win, Linux |
| `enable-standard-device-bound-session-persistence` | Device Bound Session Credentials (Standard) Persistence | `FEATURE_VALUE_TYPE(net::features::kPersistDeviceBoundSessions)` | Mac, Win, Linux |
| `enable-tab-audio-muting` | Tab audio muting UI control | `FEATURE_VALUE_TYPE(media::kEnableTabMuting)` | Desktop |
| `enable-task-manager-desktop-refresh` | Task Manager Desktop Refresh | `FEATURE_VALUE_TYPE(features::kTaskManagerDesktopRefresh)` | Desktop |
| `enable-tls13-early-data` | TLS 1.3 Early Data | `FEATURE_VALUE_TYPE(net::features::kEnableTLS13EarlyData)` | All |
| `enable-unframed-iwa` | Unframed display mode for Isolated Web Apps | `FEATURE_VALUE_TYPE(blink::features::kUnframedIwa)` | Desktop |
| `enable-unrestricted-usb` | Enable Isolated Web Apps to bypass USB restrictions | `FEATURE_VALUE_TYPE(blink::features::kUnrestrictedUsb)` | All |
| `enable-unsafe-swiftshader` | Enable unsafe SwiftShader fallback | `SINGLE_VALUE_TYPE(switches::kEnableUnsafeSwiftShader)` | All |
| `enable-unsafe-webgpu` | Unsafe WebGPU Support | `SINGLE_VALUE_TYPE(switches::kEnableUnsafeWebGPU)` | All |
| `enable-user-link-capturing-scope-extensions-pwa` | Desktop PWA Link Capturing with Scope Extensions | `FEATURE_VALUE_TYPE(features::kPwaNavigationCapturingWithScopeExtensions)` | Linux, Mac, Win |
| `enable-user-navigation-capturing-pwa` | Desktop PWA Link Capturing | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kPwaNavigationCapturing, kPwaNavigationCapturingVariations, "PwaNavigationCapturing")` | Linux, Mac, Win, CrOS |
| `enable-vulkan` | Vulkan | `FEATURE_VALUE_TYPE(features::kVulkan)` | Linux, Android |
| `enable-web-app-predictable-app-updating` | Enable predictable app updating for PWAs | `FEATURE_VALUE_TYPE(features::kWebAppPredictableAppUpdating)` | All |
| `enable-web-bluetooth` | Web Bluetooth | `FEATURE_VALUE_TYPE(features::kWebBluetooth)` | Linux |
| `enable-web-bluetooth-confirm-pairing-support` | Web Bluetooth confirm pairing support | `FEATURE_VALUE_TYPE(device::features::kWebBluetoothConfirmPairingSupport)` | Desktop |
| `enable-web-bluetooth-new-permissions-backend` | Use the new permissions backend for Web Bluetooth | `FEATURE_VALUE_TYPE(features::kWebBluetoothNewPermissionsBackend)` | Android, Desktop |
| `enable-web-payments-experimental-features` | Experimental Web Payments API features | `FEATURE_VALUE_TYPE(payments::features::kWebPaymentsExperimentalFeatures)` | All |
| `enable-webassembly-baseline` | WebAssembly baseline compiler | `FEATURE_VALUE_TYPE(features::kWebAssemblyBaseline)` | All |
| `enable-webassembly-lazy-compilation` | WebAssembly lazy compilation | `FEATURE_VALUE_TYPE(features::kWebAssemblyLazyCompilation)` | All |
| `enable-webassembly-tiering` | WebAssembly tiering | `FEATURE_VALUE_TYPE(features::kWebAssemblyTiering)` | All |
| `enable-webgl-developer-extensions` | WebGL Developer Extensions | `SINGLE_VALUE_TYPE(switches::kEnableWebGLDeveloperExtensions)` | All |
| `enable-webgl-draft-extensions` | WebGL Draft Extensions | `SINGLE_VALUE_TYPE(switches::kEnableWebGLDraftExtensions)` | All |
| `enable-webgpu-developer-features` | WebGPU Developer Features | `SINGLE_VALUE_TYPE(switches::kEnableWebGPUDeveloperFeatures)` | All |
| `enable-webmcp-testing` | WebMCP for testing | `FEATURE_VALUE_TYPE(blink::features::kWebMCPTesting)` | All |
| `enable-webrtc-allow-input-volume-adjustment` | Allow WebRTC to adjust the input volume. | `FEATURE_VALUE_TYPE(features::kWebRtcAllowInputVolumeAdjustment)` | Win, Mac, Linux |
| `enable-webrtc-apm-downmix-capture-audio-method` | WebRTC downmix capture audio method. | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kWebRtcApmDownmixCaptureAudioMethod, kWebRtcApmDownmixMethodVariations, "WebRtcApmDownmixCaptureAudioMethod")` | Desktop |
| `enable-webrtc-hide-local-ips-with-mdns` | Anonymize local IPs exposed by WebRTC. | `FEATURE_VALUE_TYPE(blink::features::kWebRtcHideLocalIpsWithMdns)` | Desktop |
| `enable-webrtc-pipewire-camera` | PipeWire Camera support | `FEATURE_VALUE_TYPE(features::kWebRtcPipeWireCamera)` | Linux |
| `enable-webrtc-use-min-max-vea-dimensions` | WebRTC Min/Max Video Encode Accelerator dimensions | `FEATURE_VALUE_TYPE(blink::features::kWebRtcUseMinMaxVEADimensions)` | All |
| `enable-webusb-device-detection` | Automatic detection of WebUSB-compatible devices | `FEATURE_VALUE_TYPE(features::kWebUsbDeviceDetection)` | Desktop |
| `enable-your-saved-info-settings-page` | Your Saved Info settings page | `FEATURE_VALUE_TYPE(autofill::features::kYourSavedInfoSettingsPage)` | Desktop |
| `enable-zero-copy` | Zero-copy rasterizer | `ENABLE_DISABLE_VALUE_TYPE(blink::switches::kEnableZeroCopy, blink::switches::kDisableZeroCopy)` | All |
| `enforce-management-disclaimer` | Enforce management disclaimer | `FEATURE_WITH_PARAMS_VALUE_TYPE(switches::kEnforceManagementDisclaimer, kPolicyDisclaimerRegistrationRetryDelayVariations, "PolicyDisclaimerRegistrationRetryDelayVariations")` | Desktop |
| `exclude-pip-from-screen-capture` | Exclude Picture-in-Picture windows from screen capture | `FEATURE_VALUE_TYPE(features::kExcludePipFromScreenCapture)` | All |
| `experimental-omnibox-labs` | Enable extension permission omnibox.directInput | `FEATURE_VALUE_TYPE(extensions_features::kExperimentalOmniboxLabs)` | Desktop |
| `experimental-web-machine-learning-neural-network` | Enables experimental WebNN API features | `FEATURE_VALUE_TYPE(webnn::mojom::features::kExperimentalWebMachineLearningNeuralNetwork)` | All |
| `extension-disable-unsupported-developer-mode-extensions` | Extension Disable Unsupported Developer | `FEATURE_VALUE_TYPE(extensions_features::kExtensionDisableUnsupportedDeveloper)` | Desktop |
| `extension-manifest-v2-deprecation-disabled` | Extension Manifest V2 Deprecation Disabled Stage | `FEATURE_VALUE_TYPE(extensions_features::kExtensionManifestV2Disabled)` | Desktop |
| `extension-manifest-v2-deprecation-unsupported` | Extension Manifest V2 Deprecation Unsupported Stage | `FEATURE_VALUE_TYPE(extensions_features::kExtensionManifestV2Unsupported)` | Desktop |
| `extensions-collapse-main-menu` | Collapse Extensions Submenu | `FEATURE_VALUE_TYPE(features::kExtensionsCollapseMainMenu)` | Desktop |
| `extensions-menu-access-control` | Extensions Menu Access Control | `FEATURE_VALUE_TYPE(extensions_features::kExtensionsMenuAccessControl)` | Desktop |
| `extensions-on-chrome-urls` | Extensions on chrome:// URLs | `SINGLE_VALUE_TYPE(extensions::switches::kExtensionsOnChromeURLs)` | All |
| `extensions-on-extension-urls` | Extensions on chrome-extension:// URLs | `SINGLE_VALUE_TYPE(extensions::switches::kExtensionsOnExtensionURLs)` | All |
| `extensions-toolbar-zero-state-variation` | Extensions Toolbar Zero State | `MULTI_VALUE_TYPE(kExtensionsToolbarZeroStateChoices)` | Desktop |
| `extract-related-searches-from-prefetched-zps-response` | Extract Related Searches from Prefetched ZPS Response | `FEATURE_VALUE_TYPE(page_content_annotations::features:: kExtractRelatedSearchesFromPrefetchedZPSResponse)` | Desktop, Android |
| `fedcm-autofill` | FedCmAutofill | `FEATURE_VALUE_TYPE(features::kFedCmAutofill)` | All |
| `fedcm-delegation` | FedCM with delegation support | `FEATURE_VALUE_TYPE(features::kFedCmDelegation)` | All |
| `fedcm-error-attribute` | FedCmErrorAttribute | `FEATURE_VALUE_TYPE(features::kFedCmErrorAttribute)` | All |
| `fedcm-idp-registration` | FedCM with IdP Registration support | `FEATURE_VALUE_TYPE(features::kFedCmIdPRegistration)` | Desktop |
| `fedcm-in-authenticator` | FedCM in Authenticator | `FEATURE_VALUE_TYPE(device::kFedCmInAuthenticator)` | All |
| `fedcm-lightweight-mode` | FedCmLightweightMode | `FEATURE_VALUE_TYPE(features::kFedCmLightweightMode)` | Desktop |
| `fedcm-metrics-endpoint` | FedCmMetricsEndpoint | `FEATURE_VALUE_TYPE(features::kFedCmMetricsEndpoint)` | All |
| `fedcm-navigation-interception` | FedCmNavigationInterception | `FEATURE_VALUE_TYPE(features::kFedCmNavigationInterception)` | All |
| `fedcm-nonce-in-params` | FedCmNonceInParams | `FEATURE_VALUE_TYPE(features::kFedCmNonceInParams)` | All |
| `fedcm-segmentation-platform` | FedCmSegmentationPlatform | `FEATURE_VALUE_TYPE(segmentation_platform::features::kSegmentationPlatformFedCmUser)` | All |
| `fedcm-well-known-endpoint-validation` | FedCmWellKnownEndpointValidation | `FEATURE_VALUE_TYPE(features::kFedCmWellKnownEndpointValidation)` | All |
| `fedcm-without-well-known-enforcement` | FedCmWithoutWellKnownEnforcement | `FEATURE_VALUE_TYPE(features::kFedCmWithoutWellKnownEnforcement)` | All |
| `field-classification-model-caching` | Enable caching field classification predictions | `FEATURE_VALUE_TYPE(autofill::features::kFieldClassificationModelCaching)` | All |
| `file-handling-icons` | File Handling Icons | `FEATURE_VALUE_TYPE(blink::features::kFileHandlingIcons)` | Desktop |
| `fill-on-account-select` | Fill passwords on account selection | `FEATURE_VALUE_TYPE(password_manager::features::kFillOnAccountSelect)` | All |
| `first-run-desktop-refresh` | First Run Desktop Refresh | `FEATURE_VALUE_TYPE(features::kFirstRunDesktopRefresh)` | Mac, Win, Linux |
| `force-color-profile` | Force color profile | `MULTI_VALUE_TYPE(kForceColorProfileChoices)` | All |
| `force-enable-webgpu-interop` | Force enable WebGPU interop | `FEATURE_VALUE_TYPE(features::kForceEnableWebGpuInterop)` | Linux |
| `force-text-direction` | Force text direction | `MULTI_VALUE_TYPE(kForceTextDirectionChoices)` | All |
| `force-ui-direction` | Force UI direction | `MULTI_VALUE_TYPE(kForceUIDirectionChoices)` | All |
| `forced-colors` | Forced Colors | `FEATURE_VALUE_TYPE(features::kForcedColors)` | All |
| `fractional-scroll-offsets` | Fractional Scroll Offsets | `FEATURE_VALUE_TYPE(features::kFractionalScrollOffsets)` | All |
| `gemini-antiscam-protections-metrics-only` | Gemini Antiscam Protection | `FEATURE_VALUE_TYPE(safe_browsing::kGeminiAntiscamProtectionForMetricsCollection)` | Mac, Win, CrOS, Android, Linux |
| `geolocation-element` | Geolocation permission control (geolocation element) | `FEATURE_VALUE_TYPE(blink::features::kGeolocationElement)` | Mac, Win, Linux, Android |
| `get-display-media-confers-activation` | getDisplayMedia() confers transient activation. | `FEATURE_VALUE_TYPE(media::kGetDisplayMediaConfersActivation)` | Desktop |
| `glic` | Glic | `FEATURE_VALUE_TYPE(features::kGlic)` | All |
| `glic-actor` | Glic actor | `FEATURE_VALUE_TYPE(features::kGlicActor)` | Desktop |
| `glic-actor-autofill` | Glic actor autofill | `FEATURE_VALUE_TYPE(features::kGlicActorAutofill)` | Desktop |
| `glic-actor-cursor` | Glic actor cursor | `FEATURE_VALUE_TYPE(features::kGlicActorUiMagicCursor)` | Desktop |
| `glic-bind-pinned-unbound-tab` | Glic Bind a Shared Tab If Unbound | `FEATURE_VALUE_TYPE(features::kGlicBindPinnedUnboundTab)` | Desktop |
| `glic-button-alt-label` | Glic Button Alt Label | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicButtonAltLabel, kGlicButtonAltLabelVariations, "GlicButtonAltLabel")` | Desktop |
| `glic-button-pressed-state` | Glic Button Pressed State | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicButtonPressedState, kGlicButtonPressedStateVariations, "GlicButtonPressedState")` | Desktop |
| `glic-capture-region` | Glic Capture Region | `FEATURE_VALUE_TYPE(features::kGlicCaptureRegion)` | All |
| `glic-daisy-chain-new-tabs` | Glic Daisy chain new tabs | `FEATURE_VALUE_TYPE(features::kGlicDaisyChainNewTabs)` | Desktop |
| `glic-default-tab-context-setting` | Glic Default Tab Context Setting | `FEATURE_VALUE_TYPE(features::kGlicDefaultTabContextSetting)` | Desktop |
| `glic-default-to-last-active-conversation` | Glic Default To Last Active Conversation | `FEATURE_VALUE_TYPE(features::kGlicDefaultToLastActiveConversation)` | Desktop |
| `glic-detached` | Glic detached-only mode | `FEATURE_VALUE_TYPE(features::kGlicDetached)` | Desktop |
| `glic-entrypoint-variations` | Glic Entrypoint Variations | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicEntrypointVariations, kGlicEntrypointVariations, "GlicEntrypointVariations")` | Desktop |
| `glic-fre-pre-warming` | Glic FRE Pre-Warming | `FEATURE_VALUE_TYPE(features::kGlicFreWarming)` | Desktop |
| `glic-guest-url-presets` | Glic guest URL presets | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicGuestUrlPresets, kGlicGuestUrlPresetTypes, "GlicGuestUrlPresets")` | Desktop |
| `glic-live-mode-only-glow` | Glic Live Mode Only Glow | `FEATURE_VALUE_TYPE(features::kGlicLiveModeOnlyGlow)` | Desktop |
| `glic-mi-tab-context-menu` | Glic Multi-Instance Tab Context Menu | `FEATURE_VALUE_TYPE(features::kGlicMITabContextMenu)` | Desktop |
| `glic-panel-reset-on-session-timeout` | Glic Panel Reset On Session Timeout | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicPanelResetOnSessionTimeout, kGlicPanelResetOnSessionTimeoutVariations, "GlicPanelResetOnSessionTimeout")` | Desktop |
| `glic-panel-reset-on-start` | Glic Panel Reset On Start | `FEATURE_VALUE_TYPE(features::kGlicPanelResetOnStart)` | Desktop |
| `glic-panel-reset-size-and-location-on-open` | Glic Panel Reset Size and Location | `FEATURE_VALUE_TYPE(features::kGlicPanelResetSizeAndLocationOnOpen)` | Desktop |
| `glic-panel-reset-top-chrome-button` | Glic Panel Reset With Top Chrome Button | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicPanelResetTopChromeButton, kGlicPanelResetTopChromeButtonVariations, "GlicPanelResetTopChromeButton")` | Desktop |
| `glic-panel-set-position-on-drag` | Glic Panel Set Position On Drag | `FEATURE_VALUE_TYPE(features::kGlicPanelSetPositionOnDrag)` | Desktop |
| `glic-pre-warming` | Glic Pre-Warming | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicWarming, kGlicWarmingVariations, "GlicWarming")` | Desktop |
| `glic-print-menu-item` | Glic Print Menu Item | `FEATURE_VALUE_TYPE(features::kGlicPrintMenuItem)` | Mac, Win, Linux |
| `glic-reset-mi-enablement-by-tier` | Glic Reset Multi-Instance Enablement By Tier | `SINGLE_VALUE_TYPE(switches::kGlicResetMultiInstanceEnabledByTier)` | Desktop |
| `glic-set-g1-for-mi` | Glic Force G1 Status for Multi-Instance | `MULTI_VALUE_TYPE(kGlicSetG1ForMultiInstance)` | Desktop |
| `glic-share-image` | Glic Share Image | `FEATURE_VALUE_TYPE(features::kGlicShareImage)` | Desktop |
| `glic-side-panel` | Glic side panel | `FEATURE_VALUE_TYPE(features::kGlicMultiInstance)` | Desktop |
| `glic-tab-restoration` | Glic Tab Restoration | `FEATURE_VALUE_TYPE(features::kGlicTabRestoration)` | Desktop |
| `glic-toolbar-height-side-panel` | Glic Use Toolbar Height Side Panel | `FEATURE_VALUE_TYPE(features::kGlicUseToolbarHeightSidePanel)` | Desktop |
| `glic-trust-first-onboarding` | Glic Trust First Onboarding | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kGlicTrustFirstOnboarding, kGlicTrustFirstOnboardingVariations, "GlicTrustFirstOnboarding")` | Desktop |
| `glic-unified-fre-screen` | Glic Unified Fre Screen | `FEATURE_VALUE_TYPE(features::kGlicUnifiedFreScreen)` | Desktop |
| `glic-z-order-changes` | Glic Z Order Changes | `FEATURE_VALUE_TYPE(features::kGlicZOrderChanges)` | Desktop |
| `group-promo-prototype` | Group Promo Prototype | `FEATURE_WITH_PARAMS_VALUE_TYPE(visited_url_ranking::features::kGroupSuggestionService, kGroupSuggestionVariations, "GroupPromoPrototype")` | All |
| `happiness-tracking-surveys-for-desktop-demo` | Happiness Tracking Surveys Demo | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kHappinessTrackingSurveysForDesktopDemo, kHappinessTrackingSurveysForDesktopDemoVariations, "HappinessTrackingSurveysForDesktopDemo")` | Desktop |
| `happy-eyeballs-v3` | Happy Eyeballs Version 3 | `FEATURE_VALUE_TYPE(net::features::kHappyEyeballsV3)` | All |
| `hardware-media-key-handling` |  | `FEATURE_VALUE_TYPE(media::kHardwareMediaKeyHandling)` | Desktop |
| `hdr-agtm` | Adaptive global tone mapping | `FEATURE_VALUE_TYPE(features::kHdrAgtm)` | All |
| `heavy-ad-privacy-mitigations` | Heavy ad privacy mitigations | `FEATURE_VALUE_TYPE(heavy_ad_intervention::features::kHeavyAdPrivacyMitigations)` | All |
| `hide-aim-omnibox-entrypoint-on-user-input` | AI Entrypoint Disabled on User Input | `FEATURE_VALUE_TYPE(omnibox::kHideAimEntrypointOnUserInput)` | Desktop |
| `history-embeddings` | History Embeddings | `FEATURE_VALUE_TYPE(history_embeddings::kHistoryEmbeddings)` | Desktop |
| `history-embeddings-answers` | History Embeddings Answers | `FEATURE_VALUE_TYPE(history_embeddings::kHistoryEmbeddingsAnswers)` | Desktop |
| `history-journeys` | History Journeys | `FEATURE_WITH_PARAMS_VALUE_TYPE(history_clusters::internal::kJourneys, kJourneysVariations, "HistoryJourneys")` | Desktop, Android |
| `history-sync-alternative-illustration` | History Sync Alternative Illustration | `FEATURE_VALUE_TYPE(tab_groups::kUseAlternateHistorySyncIllustration)` | All |
| `http-cache-custom-backend` | Use custom disk cache backend for HTTP Cache | `FEATURE_WITH_PARAMS_VALUE_TYPE(net::features::kDiskCacheBackendExperiment, kDiskCacheBackendExperimentVariations, "DiskCacheBackendExperiment")` | All |
| `http-cache-no-vary-search` | No Vary Search in Disk Cache | `FEATURE_VALUE_TYPE(net::features::kHttpCacheNoVarySearch)` | All |
| `https-first-balanced-mode` | Allow enabling Balanced Mode for HTTPS-First Mode. | `FEATURE_VALUE_TYPE(features::kHttpsFirstBalancedMode)` | Desktop, Android |
| `https-first-dialog-ui` | Dialog UI for HTTPS-First Modes | `FEATURE_VALUE_TYPE(security_interstitials::features::kHttpsFirstDialogUi)` | Desktop |
| `https-first-mode-for-typically-secure-users` | HTTPS-First Mode For Typically Secure Users | `FEATURE_VALUE_TYPE(features::kHttpsFirstModeV2ForTypicallySecureUsers)` | Desktop, Android |
| `https-first-mode-incognito` | HTTPS-First Mode in Incognito | `FEATURE_VALUE_TYPE(features::kHttpsFirstModeIncognito)` | Desktop, Android |
| `https-first-mode-incognito-new-settings` | HTTPS-First Mode in Incognito new Settings UI | `FEATURE_VALUE_TYPE(features::kHttpsFirstModeIncognitoNewSettings)` | Desktop, Android |
| `https-first-mode-v2-for-engaged-sites` | HTTPS-First Mode V2 For Engaged Sites | `FEATURE_VALUE_TYPE(features::kHttpsFirstModeV2ForEngagedSites)` | Desktop, Android |
| `https-upgrades` | HTTPS Upgrades | `FEATURE_VALUE_TYPE(features::kHttpsUpgrades)` | Desktop, Android |
| `hybrid-passkeys-in-context-menu` | Use passkey from another device in the context menu | `FEATURE_VALUE_TYPE(password_manager::features:: kWebAuthnUsePasskeyFromAnotherDeviceInContextMenu)` | Desktop |
| `idb-sqlite-backing-store` | IDB SQLite Backing Store | `FEATURE_VALUE_TYPE(features::kIdbSqliteBackingStore)` | All |
| `ignore-gpu-blocklist` | Override software rendering list | `SINGLE_VALUE_TYPE(switches::kIgnoreGpuBlocklist)` | All |
| `image-descriptions-alternative-routing` | Use alternative route for image descriptions. | `FEATURE_VALUE_TYPE(features::kImageDescriptionsAlternateRouting)` | All |
| `in-product-help-demo-mode-choice` | In-Product Help Demo Mode | `FEATURE_WITH_PARAMS_VALUE_TYPE(feature_engagement::kIPHDemoMode, feature_engagement::kIPHDemoModeChoiceVariations, "IPH_DemoMode")` | All |
| `infinite-tabs-freezing` | Infinite Tabs Freezing | `FEATURE_VALUE_TYPE(performance_manager::features::kInfiniteTabsFreezing)` | Desktop |
| `infobar-prioritization` | Infobar Prioritization | `FEATURE_VALUE_TYPE(infobars::features::kInfobarPrioritization)` | Desktop |
| `infobar-refresh` | Infobar Refresh | `FEATURE_VALUE_TYPE(features::kInfobarRefresh)` | Desktop |
| `invalidate-search-engine-choice-on-device-restore-detection` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(switches::kInvalidateSearchEngineChoiceOnDeviceRestoreDetection, kInvalidateSearchEngineChoiceOnRestoreVariations, "InvalidateSearchEngineChoiceOnDeviceRestoreDetection")` | All |
| `iph-autofill-credit-card-benefit-feature` | Enable Card Benefits in-product help bubble | `FEATURE_VALUE_TYPE(feature_engagement::kIPHAutofillCreditCardBenefitFeature)` | Desktop |
| `iph-extensions-menu-feature` | IPH Extensions Menu | `FEATURE_VALUE_TYPE(feature_engagement::kIPHExtensionsMenuFeature)` | Desktop |
| `iph-extensions-request-access-button-feature` | IPH Extensions Request Access Button Feature | `FEATURE_VALUE_TYPE(feature_engagement::kIPHExtensionsRequestAccessButtonFeature)` | Desktop |
| `isolate-origins` | Isolate additional origins | `ORIGIN_LIST_VALUE_TYPE(switches::kIsolateOrigins, "")` | All |
| `iwa-key-distribution-component-exp-cohort` | Experimental cohort for the Iwa Key Distribution component | `STRING_VALUE_TYPE(component_updater::kIwaKeyDistributionComponentExpCohort, "")` | Desktop |
| `launch-queue-stop-sending-on-reload` | Stop resending LaunchParams on user reload | `FEATURE_VALUE_TYPE(webapps::features::kLaunchQueueStopSendingOnReload)` | All |
| `left-hand-side-activity-indicators` | Left-hand side activity indicators | `FEATURE_VALUE_TYPE(content_settings::features::kLeftHandSideActivityIndicators)` | Desktop |
| `lens-aim-gradient-suggest-background` | Lens AIM M3 Side Panel Suggestions Gradient Background | `FEATURE_VALUE_TYPE(lens::features::kLensAimSuggestionsGradientBackground)` | All |
| `lens-aim-suggestions` | Lens AIM M3 Side Panel Suggestions | `FEATURE_WITH_PARAMS_VALUE_TYPE(lens::features::kLensAimSuggestions, kLensAimSuggestionsVariations, "LensAimSuggestions")` | Desktop |
| `lens-enable-raw-file-media-types` | Lens enable send raw file media types | `FEATURE_VALUE_TYPE(lens::features::kLensSendRawFileMediaTypes)` | All |
| `lens-overlay-non-blocking-privacy-notice` | Lens overlay non-blocking privacy notice | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayNonBlockingPrivacyNotice)` | Desktop |
| `lens-overlay-omnibox-entry-point` | Lens Overlay Omnibox entrypoint | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayOmniboxEntryPoint)` | Desktop |
| `lens-overlay-optimization-filter` | Lens Overlay optimization filter | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayOptimizationFilter)` | Desktop |
| `lens-overlay-permission-bubble-alt` | Lens overlay permission bubble alt appearance | `FEATURE_VALUE_TYPE(lens::features::kLensOverlayPermissionBubbleAlt)` | Desktop |
| `lens-reinvocation-affordance` | Lens search reinvocation affordance | `FEATURE_VALUE_TYPE(lens::features::kLensSearchReinvocationAffordance)` | Desktop |
| `lens-search-zero-state-csb` | Lens search zero state CSB | `FEATURE_VALUE_TYPE(lens::features::kLensSearchZeroStateCsb)` | Desktop |
| `lens-updated-feedback-entrypoint` | Lens updated feedback entrypoint | `FEATURE_VALUE_TYPE(lens::features::kLensUpdatedFeedbackEntrypoint)` | Desktop |
| `lens-video-citations` | Lens video citations | `FEATURE_VALUE_TYPE(lens::features::kLensVideoCitations)` | Desktop |
| `link-preview` | Link Preview | `FEATURE_WITH_PARAMS_VALUE_TYPE(blink::features::kLinkPreview, kLinkPreviewTriggerTypeVariations, "LinkPreview")` | Desktop |
| `local-network-access-check` | Local Network Access Checks | `FEATURE_WITH_PARAMS_VALUE_TYPE(network::features::kLocalNetworkAccessChecks, kLocalNetworkAccessChecksVariations, "LocalNetworkAccessChecks")` | All |
| `local-network-access-check-split-permissions` | Local Network Access Checks with Split Permissions | `FEATURE_VALUE_TYPE(network::features::kLocalNetworkAccessChecksSplitPermissions)` | All |
| `local-network-access-check-webrtc` | Local Network Access Checks for WebRTC | `FEATURE_VALUE_TYPE(network::features::kLocalNetworkAccessChecksWebRTC)` | All |
| `local-network-access-check-websockets` | Local Network Access Checks for WebSockets | `FEATURE_VALUE_TYPE(network::features::kLocalNetworkAccessChecksWebSockets)` | All |
| `local-network-access-check-webtransport` | Local Network Access Checks for WebTransport | `FEATURE_VALUE_TYPE(network::features::kLocalNetworkAccessChecksWebTransport)` | All |
| `main-node-annotations` | Main Node Annotations | `FEATURE_VALUE_TYPE(features::kMainNodeAnnotations)` | Desktop |
| `management-promotion-banner-flag` | Enable Management Promotion Banner | `FEATURE_VALUE_TYPE(features::kEnableManagementPromotionBanner)` | Desktop |
| `mark-all-credentials-as-leaked` | Mark all credential as leaked | `FEATURE_VALUE_TYPE(password_manager::features::kMarkAllCredentialsAsLeaked)` | Desktop |
| `mbi-mode` | MBI Scheduling Mode | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kMBIMode, kMBIModeVariations, "MBIMode")` | All |
| `mdm-errors-for-dasher-accounts-handling` | Mdm error handling for dasher accounts | `FEATURE_VALUE_TYPE(switches::kHandleMdmErrorsForDasherAccounts)` | All |
| `media-playback-while-not-visible-permission-policy` | media-playback-while-not-visible permission policy | `FEATURE_VALUE_TYPE(blink::features::kMediaPlaybackWhileNotVisiblePermissionPolicy)` | All |
| `media-route-dial-provider` | Allow cast device discovery with DIAL protocol | `FEATURE_VALUE_TYPE(media_router::kDialMediaRouteProvider)` | Desktop |
| `media-router-cast-allow-all-ips` | Connect to Cast devices on all IP addresses | `FEATURE_VALUE_TYPE(media_router::kCastAllowAllIPsFeature)` | Desktop |
| `memlog` | Chrome heap profiler start mode. | `MULTI_VALUE_TYPE(kMemlogModeChoices)` | All |
| `memlog-sampling-rate` | Heap profiling sampling interval (in bytes). | `MULTI_VALUE_TYPE(kMemlogSamplingRateChoices)` | All |
| `memlog-stack-mode` | Heap profiling stack traces type. | `MULTI_VALUE_TYPE(kMemlogStackModeChoices)` | All |
| `memory-purge-on-freeze-limit` | Memory Purge on Freeze Limit | `FEATURE_VALUE_TYPE(blink::features::kMemoryPurgeOnFreezeLimit)` | Desktop |
| `migrate-syncing-user-to-signed-in` | Migrate syncing user to signed in state | `FEATURE_VALUE_TYPE(switches::kMigrateSyncingUserToSignedIn)` | Mac, Win, Linux |
| `mobile-promo-on-desktop-force-promo-type` | Force iOS Promo Type | `FEATURE_WITH_PARAMS_VALUE_TYPE(kMobilePromoOnDesktopForcePromoType, kMobilePromoOnDesktopForcePromoTypeVariations, "MobilePromoOnDesktopForcePromo")` | All |
| `mobile-promo-on-desktop-with-qr-code` | Mobile Promo On Desktop - QRCode | `FEATURE_WITH_PARAMS_VALUE_TYPE(kMobilePromoOnDesktopWithQRCode, kMobilePromoOnDesktopWithQRCodeVariations, "MobilePromoOnDesktopWithQRCode")` | All |
| `mobile-promo-on-desktop-with-reminder` | Mobile Promo On Desktop - Reminder | `FEATURE_WITH_PARAMS_VALUE_TYPE(kMobilePromoOnDesktopWithReminder, kMobilePromoOnDesktopVariations, "MobilePromoOnDesktopWithReminder")` | All |
| `mojo-use-eventfd` | Notify about new Mojo Channel messages using eventfd | `FEATURE_VALUE_TYPE(mojo::core::kMojoUseEventFd)` | CrOS, Linux, Android |
| `most-visited-tiles-new-scoring` | Most Visited Tile: New scoring function | `FEATURE_WITH_PARAMS_VALUE_TYPE(history::kMostVisitedTilesNewScoring, kMostVisitedTilesNewScoringVariations, "MostVisitedTilesNewScoring")` | All |
| `multicast-in-direct-sockets` | Multicast in Direct Sockets API | `FEATURE_VALUE_TYPE(blink::features::kMulticastInDirectSockets)` | Desktop |
| `mute-notification-snooze-action` | Snooze action for mute notifications | `FEATURE_VALUE_TYPE(features::kMuteNotificationSnoozeAction)` | Desktop |
| `new-content-for-checkerboarded-scrolls` | Change scrolling scheduling to reduce checkerboarding | `FEATURE_VALUE_TYPE(features::kNewContentForCheckerboardedScrolls)` | All |
| `new-tab-adds-to-active-group` | Add new tabs to active tab group. | `FEATURE_VALUE_TYPE(features::kNewTabAddsToActiveGroup)` | Desktop |
| `notification-one-tap-unsubscribe-on-desktop` | Notification one-tap unsubscribe on Desktop | `FEATURE_VALUE_TYPE(features::kNotificationOneTapUnsubscribeOnDesktop)` | Desktop |
| `ntp-alpha-background-collections` | NTP Alpha Background Collections | `FEATURE_VALUE_TYPE(ntp_features::kNtpAlphaBackgroundCollections)` | Desktop |
| `ntp-background-image-error-detection` | NTP Background Image Error Detection | `FEATURE_VALUE_TYPE(ntp_features::kNtpBackgroundImageErrorDetection)` | Desktop |
| `ntp-calendar-module` | NTP Calendar Module | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpCalendarModule, kNtpCalendarModuleVariations, "DesktopNtpModules")` | Desktop |
| `ntp-composebox` | NTP Composebox | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_composebox::kNtpComposebox, kNtpComposeboxVariations, "NtpComposebox")` | Desktop |
| `ntp-customize-chrome-auto-open` | NTP Customize Chrome Auto Promo | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpCustomizeChromeAutoOpen, kNtpCustomizeChromeAutoOpenVariations, "NtpCustomizeChromeAutoOpen")` | Desktop |
| `ntp-drive-module` | NTP Drive Module | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpDriveModule, kNtpDriveModuleVariations, "DesktopNtpModules")` | Desktop |
| `ntp-drive-module-segmentation` | NTP Drive Module Segmentation | `FEATURE_VALUE_TYPE(ntp_features::kNtpDriveModuleSegmentation)` | Desktop |
| `ntp-dummy-modules` | NTP Dummy Modules | `FEATURE_VALUE_TYPE(ntp_features::kNtpDummyModules)` | Desktop |
| `ntp-feature-optimization-dismiss-modules-removal` | NTP Feature Optimization Dismiss Modules Removal | `FEATURE_VALUE_TYPE(ntp_features::kNtpFeatureOptimizationDismissModulesRemoval)` | Desktop |
| `ntp-feature-optimization-module-removal` | NTP Feature Optimization Module Removal | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpFeatureOptimizationModuleRemoval, kNtpFeatureOptimizationModuleRemovalVariations, "NtpFeatureOptimizationModuleRemoval")` | Desktop |
| `ntp-feature-optimization-shortcuts-removal` | NTP Feature Optimization Shortcuts Removal | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpFeatureOptimizationShortcutsRemoval, kNtpFeatureOptimizationShortcutsRemovalVariations, "NtpFeatureOptimizationShortcutsRemoval")` | Desktop |
| `ntp-footer` | NTP Footer | `FEATURE_VALUE_TYPE(ntp_features::kNtpFooter)` | Desktop |
| `ntp-microsoft-authentication-module` | NTP Microsoft Authentication Module | `FEATURE_VALUE_TYPE(ntp_features::kNtpMicrosoftAuthenticationModule)` | Desktop |
| `ntp-middle-slot-promo-dismissal` | NTP Middle Slot Promo Dismissal | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpMiddleSlotPromoDismissal, kNtpMiddleSlotPromoDismissalVariations, "DesktopNtpModules")` | Desktop |
| `ntp-module-sign-in-requirement` | NTP Modules Sign-in Requirement | `FEATURE_VALUE_TYPE(ntp_features::kNtpModuleSignInRequirement)` | Desktop |
| `ntp-modules-drag-and-drop` | NTP Modules Drag and Drop | `FEATURE_VALUE_TYPE(ntp_features::kNtpModulesDragAndDrop)` | Desktop |
| `ntp-next-features` | NTP Next Features | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpNextFeatures, kNtpNextVariations, "NtpNextFeatures")` | Desktop |
| `ntp-ogb-async-bar-parts` | NTP OneGoogleBar Async Bar Parts | `FEATURE_VALUE_TYPE(ntp_features::kNtpOneGoogleBarAsyncBarParts)` | Desktop |
| `ntp-outlook-calendar-module` | NTP Outlook Calendar Module | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpOutlookCalendarModule, kNtpOutlookCalendarModuleVariations, "DesktopNtpModules")` | Desktop |
| `ntp-realbox-contextual-and-trending-suggestions` | NTP Realbox Contextual and Trending Suggestions | `FEATURE_VALUE_TYPE(omnibox_feature_configs::RealboxContextualAndTrendingSuggestions:: kRealboxContextualAndTrendingSuggestions)` | Desktop |
| `ntp-realbox-cr23-theming` | Chrome Refresh Themed Realbox | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kRealboxCr23Theming, kNtpRealboxCr23ThemingVariations, "NtpRealboxCr23Theming")` | Desktop |
| `ntp-realbox-next` | NTP Realbox Next | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_realbox::kNtpRealboxNext, kNtpRealboxNextVariations, "NtpRealboxNext")` | Desktop |
| `ntp-realbox-use-google-g-icon` | NTP Realbox Google G Icon | `FEATURE_VALUE_TYPE(ntp_features::kRealboxUseGoogleGIcon)` | Desktop |
| `ntp-safe-browsing-module` | NTP Safe Browsing Module | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpSafeBrowsingModule, kNtpSafeBrowsingModuleVariations, "DesktopNtpModules")` | Desktop |
| `ntp-sharepoint-module` | NTP Sharepoint Module | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpSharepointModule, kNtpSharepointModuleVariations, "DesktopNtpModules")` | Desktop |
| `ntp-tab-groups-module` | NTP Tab Groups Module | `FEATURE_WITH_PARAMS_VALUE_TYPE(ntp_features::kNtpTabGroupsModule, kNtpTabGroupsModuleVariations, "DesktopNtpModules")` | Desktop |
| `ntp-tab-groups-module-zero-state` | NTP Tab Groups Zero State Card | `FEATURE_VALUE_TYPE(ntp_features::kNtpTabGroupsModuleZeroState)` | Desktop |
| `ntp-wallpaper-search-button` | NTP Wallpaper Search Button | `FEATURE_VALUE_TYPE(ntp_features::kNtpWallpaperSearchButton)` | Desktop |
| `ntp-wallpaper-search-button-animation` | NTP Wallpaper Search Button Animation | `FEATURE_VALUE_TYPE(ntp_features::kNtpWallpaperSearchButtonAnimation)` | Desktop |
| `offer-migration-to-dice-users` | Offer migration to Dice users | `FEATURE_VALUE_TYPE(switches::kOfferMigrationToDiceUsers)` | Desktop |
| `omit-cors-client-cert` | Omit TLS client certificates if credential mode disallows | `FEATURE_VALUE_TYPE(network::features::kOmitCorsClientCert)` | All |
| `omnibox-adjust-indentation` | Adjust Indentation for Omnibox Text and Suggestions | `FEATURE_VALUE_TYPE(omnibox_feature_configs::AdjustOmniboxIndent::kAdjustOmniboxIndent)` | Desktop |
| `omnibox-allow-ai-mode-matches` | Omnibox Allow AI Mode Matches | `FEATURE_VALUE_TYPE(omnibox_feature_configs::AiMode::kAllowAiModeMatches)` | Desktop |
| `omnibox-calc-provider` | Omnibox calc provider | `FEATURE_VALUE_TYPE(omnibox_feature_configs::CalcProvider::kCalcProvider)` | All |
| `omnibox-contextual-search-on-focus-suggestions` | Omnibox contextual search on focus suggestions | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox_feature_configs::ContextualSearch:: kOmniboxContextualSearchOnFocusSuggestions, kOmniboxContextualSearchOnFocusSuggestionsVariations, "OmniboxContextualSearchOnFocusSuggestions")` | Desktop |
| `omnibox-contextual-suggestions` | Omnibox contextual suggestions | `FEATURE_VALUE_TYPE(omnibox_feature_configs::ContextualSearch:: kOmniboxContextualSuggestions)` | Desktop |
| `omnibox-debug-logs` |  | `FEATURE_VALUE_TYPE(omnibox::kOmniboxDebugLogs)` | Desktop |
| `omnibox-drive-suggestions-no-sync-requirement` | Omnibox Google Drive Document suggestions don't require Chrome Sync | `FEATURE_VALUE_TYPE(omnibox::kDocumentProviderNoSyncRequirement)` | Desktop |
| `omnibox-dynamic-max-autocomplete` | Omnibox Dynamic Max Autocomplete | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kDynamicMaxAutocomplete, kOmniboxDynamicMaxAutocompleteVariations, "OmniboxBundledExperimentV1")` | All |
| `omnibox-enterprise-search-aggregator` | Omnibox search aggregator | `FEATURE_VALUE_TYPE(omnibox_feature_configs::SearchAggregatorProvider:: kSearchAggregatorProvider)` | Desktop |
| `omnibox-focus-triggers-web-and-srp-zero-suggest` | Omnibox on-focus suggestions on web and SRP | `FEATURE_VALUE_TYPE(omnibox::kFocusTriggersWebAndSRPZeroSuggest)` | Desktop |
| `omnibox-force-allowed-to-be-default` | Omnibox Force Allowed To Be Default | `FEATURE_VALUE_TYPE(omnibox_feature_configs::ForceAllowedToBeDefault:: kForceAllowedToBeDefault)` | Desktop |
| `omnibox-grouping-framework-non-zps` | Omnibox Grouping Framework for Typed Suggestions | `FEATURE_VALUE_TYPE(omnibox::kGroupingFrameworkForNonZPS)` | All |
| `omnibox-hide-contextual-group-headers` | Hide contextual suggestion group headers in the Omnibox popup | `FEATURE_VALUE_TYPE(omnibox::kHideContextualGroupHeaders)` | Desktop |
| `omnibox-hide-suggestion-group-headers` | Hide suggestion group headers in the Omnibox popup | `FEATURE_VALUE_TYPE(omnibox::kHideSuggestionGroupHeaders)` | Desktop |
| `omnibox-local-history-zero-suggest-beyond-ntp` | Allow local history zero-prefix suggestions beyond NTP | `FEATURE_VALUE_TYPE(omnibox::kLocalHistoryZeroSuggestBeyondNTP)` | All |
| `omnibox-max-zero-suggest-matches` | Omnibox Max Zero Suggest Matches | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kMaxZeroSuggestMatches, kMaxZeroSuggestMatchesVariations, "OmniboxBundledExperimentV1")` | Desktop, Android |
| `omnibox-mia-zps` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox_feature_configs::MiaZPS::kOmniboxMiaZPS, kOmniboxMiaZpsVariations, "OmniboxMiaZpsVariations")` | All |
| `omnibox-ml-log-url-scoring-signals` | Log Omnibox URL Scoring Signals | `FEATURE_VALUE_TYPE(omnibox::kLogUrlScoringSignals)` | All |
| `omnibox-ml-url-piecewise-mapped-search-blending` | Omnibox ML Scoring with Piecewise Score Mapping | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kMlUrlPiecewiseMappedSearchBlending, kMlUrlPiecewiseMappedSearchBlendingVariations, "MlUrlPiecewiseMappedSearchBlending")` | All |
| `omnibox-ml-url-score-caching` | Omnibox ML URL Score Caching | `FEATURE_VALUE_TYPE(omnibox::kMlUrlScoreCaching)` | All |
| `omnibox-ml-url-scoring` | Omnibox ML URL Scoring | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kMlUrlScoring, kOmniboxMlUrlScoringVariations, "MlUrlScoring")` | All |
| `omnibox-ml-url-scoring-model` | Omnibox URL Scoring Model | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kUrlScoringModel, kUrlScoringModelVariations, "MlUrlScoring")` | All |
| `omnibox-ml-url-search-blending` | Omnibox ML URL Search Blending | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kMlUrlSearchBlending, kMlUrlSearchBlendingVariations, "MlUrlScoring")` | All |
| `omnibox-on-device-tail-suggestions` | Omnibox on device tail suggestions | `FEATURE_VALUE_TYPE(omnibox::kOnDeviceTailModel)` | All |
| `omnibox-rich-autocompletion-promising` | Omnibox Rich Autocompletion Promising Combinations | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kRichAutocompletion, kOmniboxRichAutocompletionPromisingVariations, "OmniboxBundledExperimentV1")` | Desktop |
| `omnibox-search-client-prefetch` | Omnibox client prefetch Search | `FEATURE_VALUE_TYPE(kSearchNavigationPrefetch)` | All |
| `omnibox-search-prefetch` | Omnibox prefetch Search | `FEATURE_WITH_PARAMS_VALUE_TYPE(kSearchPrefetchServicePrefetching, kSearchPrefetchServicePrefetchingVariations, "SearchSuggestionPrefetch")` | All |
| `omnibox-show-popup-on-mouse-released` | Show omnibox suggestions popup on mouse released | `FEATURE_VALUE_TYPE(omnibox::kShowPopupOnMouseReleased)` | Desktop |
| `omnibox-starter-pack-expansion` | Expansion pack for the Site search starter pack | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kStarterPackExpansion, kOmniboxStarterPackExpansionVariations, "StarterPackExpansion")` | Desktop |
| `omnibox-starter-pack-iph` | IPH message for the Site search starter pack | `FEATURE_VALUE_TYPE(omnibox::kStarterPackIPH)` | Desktop |
| `omnibox-toolbelt` | Omnibox toolbelt | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox_feature_configs::Toolbelt::kOmniboxToolbelt, kOmniboxToolbeltVariations, "OmniboxToolbelt")` | Desktop |
| `omnibox-ui-max-autocomplete-matches` | Omnibox UI Max Autocomplete Matches | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kUIExperimentMaxAutocompleteMatches, kOmniboxUIMaxAutocompleteMatchesVariations, "OmniboxBundledExperimentV1")` | Desktop, Android |
| `omnibox-url-suggestions-on-focus` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox_feature_configs::OmniboxUrlSuggestionsOnFocus:: kOmniboxUrlSuggestionsOnFocus, kOmniboxUrlSuggestionsOnFocusVariations, "OmniboxUrlSuggestionsOnFocus")` | Desktop |
| `omnibox-zero-suggest-prefetch-debouncing` | Omnibox Zero Prefix Suggest Prefetch Request Debouncing | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kZeroSuggestPrefetchDebouncing, kOmniboxZeroSuggestPrefetchDebouncingVariations, "OmniboxZeroSuggestPrefetchDebouncing")` | All |
| `omnibox-zero-suggest-prefetching-on-srp` | Omnibox Zero Prefix Suggestion Prefetching on SRP | `FEATURE_VALUE_TYPE(omnibox::kZeroSuggestPrefetchingOnSRP)` | All |
| `omnibox-zero-suggest-prefetching-on-web` | Omnibox Zero Prefix Suggestion Prefetching on the Web | `FEATURE_VALUE_TYPE(omnibox::kZeroSuggestPrefetchingOnWeb)` | All |
| `omnibox-zps-suggestion-limit` |  | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox_feature_configs::OmniboxZpsSuggestionLimit:: kOmniboxZpsSuggestionLimit, kOmniboxZpsSuggestionLimitVariations, "OmniboxZpsSuggestionLimit")` | Desktop |
| `open-all-profiles-from-profile-picker-experiment` | Add button to open all profiles from profile picker | `FEATURE_VALUE_TYPE(switches::kOpenAllProfilesFromProfilePickerExperiment)` | Mac, Win, Linux |
| `open-dragged-links-same-tab` | Open Dragged Links in the Same Tab | `FEATURE_VALUE_TYPE(blink::features::kSupportOpeningDraggedLinksInSameTab)` | Desktop |
| `optimization-guide-debug-logs` | Enable optimization guide debug logs | `SINGLE_VALUE_TYPE(optimization_guide::switches::kDebugLoggingEnabled)` | All |
| `optimization-guide-enable-dogfood-logging` | Enable optimization guide dogfood logging | `SINGLE_VALUE_TYPE(optimization_guide::switches::kEnableModelQualityDogfoodLogging)` | All |
| `optimization-guide-on-device-model` | Enables optimization guide on device | `FEATURE_WITH_PARAMS_VALUE_TYPE(optimization_guide::features::kOnDeviceModelPerformanceParams, kOptimizationGuideOnDeviceModelVariations, "OptimizationGuideOnDeviceModel")` | Desktop |
| `organic-repeatable-queries` | Organic repeatable queries in Most Visited tiles | `FEATURE_WITH_PARAMS_VALUE_TYPE(history::kOrganicRepeatableQueries, kOrganicRepeatableQueriesVariations, "OrganicRepeatableQueries")` | Desktop, Android |
| `origin-agent-cluster-default` | Origin-keyed Agent Clusters by default | `FEATURE_VALUE_TYPE(blink::features::kOriginAgentClusterDefaultEnabled)` | All |
| `origin-keyed-processes-by-default` | Origin-keyed Processes by default | `FEATURE_VALUE_TYPE(features::kOriginKeyedProcessesByDefault)` | All |
| `overlay-strategies` | Select HW overlay strategies | `MULTI_VALUE_TYPE(kOverlayStrategiesChoices)` | All |
| `page-actions-migration` | Page actions migration | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kPageActionsMigration, kPageActionsMigrationVariations, "PageActionsMigration")` | Desktop |
| `page-content-annotations` | Page content annotations | `FEATURE_WITH_PARAMS_VALUE_TYPE(page_content_annotations::features::kPageContentAnnotations, kPageContentAnnotationsVariations, "PageContentAnnotations")` | Desktop, Android |
| `page-content-annotations-remote-page-metadata` | Page content annotations - Remote page metadata | `FEATURE_WITH_PARAMS_VALUE_TYPE(page_content_annotations::features::kRemotePageMetadata, kRemotePageMetadataVariations, "RemotePageMetadata")` | Desktop, Android |
| `page-visibility-page-content-annotations` | Page visibility content annotations | `FEATURE_VALUE_TYPE(page_content_annotations::features:: kPageVisibilityPageContentAnnotations)` | Desktop, Android |
| `partition-alloc-with-advanced-checks` | PartitionAlloc with Advanced Checks | `FEATURE_WITH_PARAMS_VALUE_TYPE(base::features::kPartitionAllocWithAdvancedChecks, kPartitionAllocWithAdvancedChecksEnabledProcessesOptions, "PartitionAllocWithAdvancedChecks")` | All |
| `partition-visited-link-database-with-self-links` | Partition the Visited Link Database, including 'self-links' | `FEATURE_VALUE_TYPE(blink::features::kPartitionVisitedLinkDatabaseWithSelfLinks)` | All |
| `passkey-unlock-error-ui` | Passkey Unlock Error UI | `FEATURE_VALUE_TYPE(device::kPasskeyUnlockErrorUi)` | Desktop |
| `passkey-unlock-manager` | Passkey Unlock Manager | `FEATURE_VALUE_TYPE(device::kPasskeyUnlockManager)` | Desktop |
| `password-form-clientside-classifier` | Clientside password form classifier. | `FEATURE_VALUE_TYPE(password_manager::features::kPasswordFormClientsideClassifier)` | All |
| `password-form-grouped-affiliations` | Grouped affiliation password suggestions | `FEATURE_VALUE_TYPE(password_manager::features::kPasswordFormGroupedAffiliations)` | All |
| `password-upload-ui-update` | Password Upload UI Update | `FEATURE_VALUE_TYPE(switches::kPasswordUploadUiUpdate)` | Linux, Mac, Win |
| `pdf-ink2` | PDF Ink Signatures | `FEATURE_WITH_PARAMS_VALUE_TYPE(chrome_pdf::features::kPdfInk2, kPdfInk2Variations, "PdfInk2")` | Desktop |
| `pdf-oopif` | OOPIF for PDF Viewer | `FEATURE_VALUE_TYPE(chrome_pdf::features::kPdfOopif)` | Desktop |
| `pdf-portfolio` | PDF portfolio | `FEATURE_VALUE_TYPE(chrome_pdf::features::kPdfPortfolio)` | Desktop |
| `pdf-save-to-drive` | Save PDF to Drive | `FEATURE_VALUE_TYPE(chrome_pdf::features::kPdfSaveToDrive)` | Desktop |
| `pdf-use-skia-renderer` | Use Skia Renderer | `FEATURE_VALUE_TYPE(chrome_pdf::features::kPdfUseSkiaRenderer)` | Desktop |
| `pdf-xfa-forms` | PDF XFA support | `FEATURE_VALUE_TYPE(chrome_pdf::features::kPdfXfaSupport)` | Desktop |
| `permission-element` | Page embedded permission control (permission element) | `FEATURE_VALUE_TYPE(blink::features::kPermissionElement)` | Mac, Win, CrOS, Android, Linux |
| `permission-site-settings-radio-button` | Permission radio buttons in Site Settings | `FEATURE_VALUE_TYPE(permissions::features::kPermissionSiteSettingsRadioButton)` | All |
| `permissions-ai-p92` | PermissionsAIP92 | `FEATURE_VALUE_TYPE(permissions::features::kPermissionsAIP92)` | All |
| `permissions-ai-v3` | PermissionsAIv3 | `FEATURE_VALUE_TYPE(permissions::features::kPermissionsAIv3)` | Desktop |
| `permissions-ai-v4` | PermissionsAIv4 | `FEATURE_VALUE_TYPE(permissions::features::kPermissionsAIv4)` | All |
| `permissions-gesture-gated-prompts` | Permissions Gesture Gated Prompts | `FEATURE_WITH_PARAMS_VALUE_TYPE(permissions::features::kPermissionsGestureGatedPrompts, kPermissionsGestureGatedPromptsVariations, "PermissionsGestureGatedPrompts")` | All |
| `picture-in-picture-show-window-animation` | Picture-in-Picture show window animation | `FEATURE_VALUE_TYPE(media::kPictureInPictureShowWindowAnimation)` | Desktop |
| `policy-promotion-banner-flag` | Enable Policy Promotion Banner | `FEATURE_VALUE_TYPE(features::kEnablePolicyPromotionBanner)` | Desktop |
| `predictable-reported-quota` | Predictable Reported Quota | `FEATURE_VALUE_TYPE(storage::features::kStaticStorageQuota)` | All |
| `prefetch-bookmarkbar-trigger` | BookmarkBarPrefetch | `FEATURE_VALUE_TYPE(features::kBookmarkTriggerForPrefetch)` | Desktop |
| `prefetch-new-tab-page-trigger` | NewTabPagePrefetch | `FEATURE_VALUE_TYPE(features::kNewTabPageTriggerForPrefetch)` | Desktop |
| `prerender-activation-by-form-submission` | Prerender Activation By Form Submission | `FEATURE_VALUE_TYPE(blink::features::kPrerenderActivationByFormSubmission)` | All |
| `prerender-early-document-lifecycle-update` | Prerender more document lifecycle phases | `FEATURE_VALUE_TYPE(blink::features::kPrerender2EarlyDocumentLifecycleUpdate)` | All |
| `prerender-until-script` | Prerender Until Script | `FEATURE_VALUE_TYPE(blink::features::kPrerenderUntilScript)` | All |
| `prerender2` | Prerendering | `FEATURE_VALUE_TYPE(blink::features::kPrerender2)` | All |
| `prerender2-reuse-host` | Prerender Reuse Host | `FEATURE_VALUE_TYPE(features::kPrerender2ReuseHost)` | All |
| `price-tracking-subscription-service-locale-key` |  | `FEATURE_VALUE_TYPE(commerce::kPriceTrackingSubscriptionServiceLocaleKey)` | Android, Desktop |
| `price-tracking-subscription-service-product-version` |  | `FEATURE_VALUE_TYPE(commerce::kPriceTrackingSubscriptionServiceProductVersion)` | Android, Desktop |
| `privacy-policy-insights` | Privacy Policy Insights | `FEATURE_VALUE_TYPE(page_info::kPrivacyPolicyInsights)` | Desktop |
| `privacy-sandbox-ad-topics-content-parity` | Privacy Sandbox Ad Topics Content Parity | `FEATURE_VALUE_TYPE(privacy_sandbox::kPrivacySandboxAdTopicsContentParity)` | All |
| `privacy-sandbox-ads-api-ux-enhancements` | Privacy Sandbox Ads API UX Enhancements | `FEATURE_VALUE_TYPE(privacy_sandbox::kPrivacySandboxAdsApiUxEnhancements)` | All |
| `privacy-sandbox-enrollment-overrides` | Privacy Sandbox Enrollment Overrides | `ORIGIN_LIST_VALUE_TYPE(privacy_sandbox::kPrivacySandboxEnrollmentOverrides, "")` | All |
| `privacy-sandbox-internals` | Privacy Sandbox Internals Page | `FEATURE_VALUE_TYPE(privacy_sandbox::kPrivacySandboxInternalsDevUI)` | All |
| `private-metrics-enable-puma` | Enable Private User Metrics | `FEATURE_VALUE_TYPE(metrics::private_metrics::kPrivateMetricsPuma)` | All |
| `private-metrics-enable-puma-rc` | Enable Private User Metrics for Regional Capabilities | `FEATURE_VALUE_TYPE(metrics::private_metrics::kPrivateMetricsPumaRc)` | All |
| `product-specifications` |  | `FEATURE_VALUE_TYPE(commerce::kProductSpecifications)` | Desktop |
| `profile-creation-decline-signin-cta-experiment` | Enable CTA experiment for sign-in level up | `FEATURE_VALUE_TYPE(switches::kProfileCreationDeclineSigninCTAExperiment)` | Mac, Win, Linux |
| `profile-creation-friction-reduction-experiment-prefill-name-requirement` |  | `FEATURE_VALUE_TYPE(switches:: kProfileCreationFrictionReductionExperimentPrefillNameRequirement)` | Mac, Win, Linux |
| `profile-creation-friction-reduction-experiment-remove-signin-step` |  | `FEATURE_VALUE_TYPE(switches:: kProfileCreationFrictionReductionExperimentRemoveSigninStep)` | Mac, Win, Linux |
| `profile-creation-friction-reduction-experiment-skip-customize-profile` |  | `FEATURE_VALUE_TYPE(switches:: kProfileCreationFrictionReductionExperimentSkipCustomizeProfile)` | Mac, Win, Linux |
| `profile-picker-text-variations` | Profile Picker Text Variations | `FEATURE_WITH_PARAMS_VALUE_TYPE(switches::kProfilePickerTextVariations, kProfilePickerTextVariations, "ProfilePickerTextVariations")` | Linux, Mac, Win |
| `profile-signals-reporting-enabled` | Profile Signals Reporting Enabled | `FEATURE_VALUE_TYPE(enterprise_signals::features::kProfileSignalsReportingEnabled)` | All |
| `profiles-reordering` | Profiles Reordering | `FEATURE_VALUE_TYPE(switches::kProfilesReordering)` | Desktop |
| `prompt-api-for-gemini-nano` | Prompt API for Gemini Nano | `FEATURE_WITH_PARAMS_VALUE_TYPE(blink::features::kAIPromptAPI, kAILangsVariation, "kAIPromptAPI")` | Desktop |
| `prompt-api-for-gemini-nano-multimodal-input` | Prompt API for Gemini Nano with Multimodal Input | `FEATURE_VALUE_TYPE(blink::features::kAIPromptAPIMultimodalInput)` | Desktop |
| `proofreader-api-for-gemini-nano` | Proofreader API for Gemini Nano | `FEATURE_VALUE_TYPE(blink::features::kAIProofreadingAPI)` | Desktop |
| `protected-audience-debug-token` | Protected Audiences Consented Debug Token | `STRING_VALUE_TYPE(switches::kProtectedAudiencesConsentedDebugToken, "")` | All |
| `pulseaudio-loopback-for-cast` | Linux System Audio Loopback for Cast (pulseaudio) | `FEATURE_VALUE_TYPE(media::kPulseaudioLoopbackForCast)` | Linux |
| `pulseaudio-loopback-for-screen-share` | Linux System Audio Loopback for Screen Sharing (pulseaudio) | `FEATURE_VALUE_TYPE(media::kPulseaudioLoopbackForScreenShare)` | Linux |
| `pwa-update-dialog-for-icon` | Enable PWA install update dialog for icon changes | `FEATURE_VALUE_TYPE(features::kPwaUpdateDialogForIcon)` | All |
| `pwm-show-suggestions-on-autofocus` | Showing password suggestions on autofocused password forms | `FEATURE_VALUE_TYPE(password_manager::features::kShowSuggestionsOnAutofocus)` | All |
| `rcaps-dynamic-profile-country` | Dynamic Profile Country | `FEATURE_VALUE_TYPE(switches::kDynamicProfileCountry)` | All |
| `read-anything-docs-integration` | Reading Mode Google Docs Integration | `FEATURE_VALUE_TYPE(features::kReadAnythingDocsIntegration)` | Desktop |
| `read-anything-docs-load-more-button` | Reading Mode Google Docs Load More Button | `FEATURE_VALUE_TYPE(features::kReadAnythingDocsLoadMoreButton)` | Desktop |
| `read-anything-images-via-algorithm` | Reading Mode with images added via algorithm | `FEATURE_VALUE_TYPE(features::kReadAnythingImagesViaAlgorithm)` | Desktop |
| `read-anything-immersive-reading-mode` | Reading Mode Experimental Immersive Mode | `FEATURE_VALUE_TYPE(features::kImmersiveReadAnything)` | Desktop |
| `read-anything-line-focus` | Reading Mode Line Focus | `FEATURE_VALUE_TYPE(features::kReadAnythingLineFocus)` | Desktop |
| `read-anything-omnibox-chip` | Reading Mode Omnibox Chip | `FEATURE_VALUE_TYPE(features::kReadAnythingOmniboxChip)` | Desktop |
| `read-anything-read-aloud-phrase-highlighting` | Reading Mode Read Aloud Phrase Highlighting | `FEATURE_VALUE_TYPE(features::kReadAnythingReadAloudPhraseHighlighting)` | Desktop |
| `read-anything-read-aloud-ts-text-segmentation` | Reading Mode Read Aloud Experimental Text Segmentation | `FEATURE_VALUE_TYPE(features::kReadAnythingReadAloudTSTextSegmentation)` | Desktop |
| `read-anything-with-readability-enabled` | Reading Mode Experimental Webpage Distilation | `FEATURE_VALUE_TYPE(features::kReadAnythingWithReadability)` | Desktop |
| `record-web-app-debug-info` | Record web app debug info | `FEATURE_VALUE_TYPE(features::kRecordWebAppDebugInfo)` | Desktop |
| `reduce-transfer-size-updated-ipc` | Reduce TransferSizeUpdated IPC | `FEATURE_VALUE_TYPE(network::features::kReduceTransferSizeUpdatedIPC)` | All |
| `reduce-user-agent-data-linux-platform-version` | Reduce Linux platform version Client Hint | `FEATURE_VALUE_TYPE(blink::features::kReduceUserAgentDataLinuxPlatformVersion)` | Linux |
| `region-capture-cross-tab` | Region Capture cross-tab | `FEATURE_VALUE_TYPE(features::kRegionCaptureOfOtherTabs)` | Desktop |
| `reintroduce-hybrid-passkey-entry-point` | Reintroduce hybrid passkey entry point | `FEATURE_VALUE_TYPE(password_manager::features:: kAutofillReintroduceHybridPasskeyDropdownItem)` | Desktop |
| `related-website-sets-permission-grants` | Show permission grants from Related Website Sets | `FEATURE_VALUE_TYPE(permissions::features::kShowRelatedWebsiteSetsPermissionGrants)` | Desktop, Android |
| `render-document` | Enable RenderDocument | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kRenderDocument, kRenderDocumentVariations, "RenderDocument")` | All |
| `renderer-side-content-decoding` | Renderer-side content decoding | `FEATURE_VALUE_TYPE(network::features::kRendererSideContentDecoding)` | All |
| `replace-sync-promos-with-sign-in-promos-desktop` | Replace all sync-related UI with sign-in ones | `MULTI_VALUE_TYPE(kReplaceSyncPromosWithSignInPromosChoices)` | Desktop |
| `responsive-iframes` | Responsive Iframes | `FEATURE_VALUE_TYPE(blink::features::kResponsiveIframes)` | All |
| `rewriter-api-for-gemini-nano` | Rewriter API for Gemini Nano | `FEATURE_WITH_PARAMS_VALUE_TYPE(blink::features::kAIRewriterAPI, kAILangsVariation, "kAIRewriterAPI")` | Desktop |
| `root-scrollbar-follows-browser-theme` |  | `FEATURE_VALUE_TYPE(blink::features::kRootScrollbarFollowsBrowserTheme)` | Linux, Win |
| `route-matching` | Route matching | `FEATURE_VALUE_TYPE(blink::features::kRouteMatching)` | All |
| `saas-usage-reporting` | Saas Usage Reporting | `FEATURE_VALUE_TYPE(enterprise_reporting::kSaasUsageReporting)` | Linux, Mac, Win |
| `safe-browsing-local-lists-use-sbv5` | Safe Browsing Local Lists use v5 API | `FEATURE_VALUE_TYPE(safe_browsing::kLocalListsUseSBv5)` | All |
| `safety-check-unused-site-permissions` | Permission Module for unused sites in Safety Check | `FEATURE_WITH_PARAMS_VALUE_TYPE(content_settings::features::kSafetyCheckUnusedSitePermissions, kSafetyCheckUnusedSitePermissionsVariations, "SafetyCheckUnusedSitePermissions")` | All |
| `safety-hub-disruptive-notification-revocation` | Safety Hub - Disruptive notification revocation | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kSafetyHubDisruptiveNotificationRevocation, kSafetyHubDisruptiveNotificationRevocationVariations, "SafetyHubDisruptiveNotificationRevocation")` | All |
| `safety-hub-unused-permission-revocation-for-all-surfaces` | Safety Hub - unused permission revocation from all surfaces | `FEATURE_VALUE_TYPE(permissions::features:: kSafetyHubUnusedPermissionRevocationForAllSurfaces)` | All |
| `save-passwords-contextual-ui` | Save Password Contextual UI | `FEATURE_VALUE_TYPE(features::kSavePasswordsContextualUi)` | Desktop |
| `sct-auditing` | SCT auditing | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kSCTAuditing, kSCTAuditingVariations, "SCTAuditingVariations")` | Desktop |
| `send-tab-ios-push-notifications` | Send tab to self iOS push notifications | `FEATURE_WITH_PARAMS_VALUE_TYPE(send_tab_to_self::kSendTabToSelfIOSPushNotifications, kSendTabIOSPushNotificationsVariations, "SendTabToSelfIOSPushNotifications")` | All |
| `separate-local-and-account-search-engines` | Separate local and account search engines | `FEATURE_VALUE_TYPE(syncer::kSeparateLocalAndAccountSearchEngines)` | Desktop |
| `separate-local-and-account-themes` | Separate local and account themes | `FEATURE_VALUE_TYPE(syncer::kSeparateLocalAndAccountThemes)` | Desktop |
| `service-worker-synthetic-response` | ServiceWorkerSyntheticResponse | `FEATURE_VALUE_TYPE(blink::features::kServiceWorkerSyntheticResponse)` | All |
| `shared-data-types-kill-switch` | Data Sharing Versioning Test Scenarios | `MULTI_VALUE_TYPE(kDataSharingVersioningStateChoices)` | All |
| `sharing-desktop-screenshots` | Desktop Screenshots | `FEATURE_VALUE_TYPE(sharing_hub::kDesktopScreenshots)` | Desktop |
| `shopping-alternate-server` |  | `FEATURE_VALUE_TYPE(commerce::kShoppingAlternateServer)` | Android, Desktop |
| `shopping-list` |  | `FEATURE_VALUE_TYPE(commerce::kShoppingList)` | Android, Desktop |
| `show-autofill-type-predictions` | Show Autofill predictions | `FEATURE_WITH_PARAMS_VALUE_TYPE(autofill::features::debug::kAutofillShowTypePredictions, kAutofillShowTypePredictionsVariations, "AutofillShowTypePredictions")` | All |
| `show-overdraw-feedback` | Show overdraw feedback | `SINGLE_VALUE_TYPE(switches::kShowOverdrawFeedback)` | All |
| `show-profile-picker-to-all-users-experiment` | Show profile picker to all users | `FEATURE_VALUE_TYPE(switches::kShowProfilePickerToAllUsersExperiment)` | Mac, Win, Linux |
| `site-isolation-trial-opt-out` | Disable site isolation | `MULTI_VALUE_TYPE(kSiteIsolationOptOutChoices)` | All |
| `skia-graphite` | Skia Graphite | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kSkiaGraphite, kSkiaGraphiteVariations, "SkiaGraphite")` | All |
| `skia-graphite-precompilation` | Skia Graphite Precompilation | `FEATURE_VALUE_TYPE(features::kSkiaGraphitePrecompilation)` | All |
| `smooth-scrolling` | Smooth Scrolling | `ENABLE_DISABLE_VALUE_TYPE(switches::kEnableSmoothScrolling, switches::kDisableSmoothScrolling)` | Linux, CrOS, Win, Android |
| `soft-navigation-heuristics` | Soft Navigation Heuristics | `FEATURE_VALUE_TYPE(blink::features::kSoftNavigationHeuristics)` | All |
| `strict-origin-isolation` | Strict-Origin-Isolation | `FEATURE_VALUE_TYPE(features::kStrictOriginIsolation)` | All |
| `structured-dns-errors` | Structured DNS Errors | `FEATURE_VALUE_TYPE(net::features::kUseStructuredDnsErrors)` | All |
| `supervised-user-block-interstitial-v3` | Enable URL filter interstitial V3 | `FEATURE_VALUE_TYPE(supervised_user::kSupervisedUserBlockInterstitialV3)` | All |
| `supervised-user-merge-device-parental-controls-and-family-link-prefs` |  | `FEATURE_VALUE_TYPE(supervised_user:: kSupervisedUserMergeDeviceParentalControlsAndFamilyLinkPrefs)` | All |
| `supervised-user-use-url-filtering-service` | Use URL filtering service | `FEATURE_VALUE_TYPE(supervised_user::kSupervisedUserUseUrlFilteringService)` | All |
| `symphonia-audio-decoding` | Symphonia Audio Decoding | `FEATURE_VALUE_TYPE(media::kSymphoniaAudioDecoding)` | All |
| `sync-autofill-wallet-credential-data` | Sync Autofill Wallet Credential Data | `FEATURE_VALUE_TYPE(syncer::kSyncAutofillWalletCredentialData)` | All |
| `system-keyboard-lock` | Experimental system keyboard lock | `FEATURE_VALUE_TYPE(features::kSystemKeyboardLock)` | Desktop |
| `tab-capture-infobar-links` | Navigation links in the tab-sharing bar | `FEATURE_VALUE_TYPE(features::kTabCaptureInfobarLinks)` | Desktop |
| `tab-group-home` |  | `FEATURE_VALUE_TYPE(tabs::kTabGroupHome)` | Desktop |
| `tab-group-menu-improvements` | Add context menu when left-clicking a tab group | `FEATURE_VALUE_TYPE(features::kTabGroupMenuImprovements)` | Desktop |
| `tab-group-more-entry-points` | Make options menus to include more tab group actions | `FEATURE_VALUE_TYPE(features::kTabGroupMenuMoreEntryPoints)` | Desktop |
| `tab-groups-focusing` | Tab Groups Focusing | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kTabGroupsFocusing, kTabGroupsFocusingVariations, "TabGroupsFocusing")` | Desktop |
| `test-third-party-cookie-phaseout` | Test Third Party Cookie Phaseout | `SINGLE_VALUE_TYPE(network::switches::kTestThirdPartyCookiePhaseout)` | All |
| `text-based-audio-descriptions` | Enable audio descriptions. | `FEATURE_VALUE_TYPE(features::kTextBasedAudioDescription)` | All |
| `text-safety-classifier` | Text Safety Classifier | `FEATURE_WITH_PARAMS_VALUE_TYPE(optimization_guide::features::kTextSafetyClassifier, kTextSafetyClassifierVariations, "TextSafetyClassifier")` | Desktop |
| `three-button-password-save-dialog` | Three Button Password Save Dialog | `FEATURE_VALUE_TYPE(features::kThreeButtonPasswordSaveDialog)` | Desktop |
| `throttle-main-thread-to-60hz` | throttle-main-thread-to-60hz | `FEATURE_VALUE_TYPE(features::kThrottleMainFrameTo60Hz)` | All |
| `tint-composited-content` | Tint composited content | `SINGLE_VALUE_TYPE(switches::kTintCompositedContent)` | All |
| `tls-trust-anchor-ids` | TLS Trust Anchor IDs | `FEATURE_VALUE_TYPE(net::features::kTLSTrustAnchorIDs)` | All |
| `top-chrome-touch-ui` | Touch UI Layout | `MULTI_VALUE_TYPE(kTopChromeTouchUiChoices)` | Desktop |
| `tpcd-heuristics-grants` | Third-party Cookie Grants Heuristics Testing | `FEATURE_WITH_PARAMS_VALUE_TYPE(content_settings::features::kTpcdHeuristicsGrants, kTpcdHeuristicsGrantsVariations, "TpcdHeuristicsGrants")` | All |
| `tpcd-metadata-grants` | Third-Party Cookie Deprecation Metadata Grants for Testing | `FEATURE_VALUE_TYPE(net::features::kTpcdMetadataGrants)` | All |
| `translate-open-settings` | Translate Open Settings | `FEATURE_VALUE_TYPE(language::kTranslateOpenSettings)` | Desktop |
| `translation-api-streaming-by-sentence` | Translation API streaming split by sentence | `FEATURE_VALUE_TYPE(on_device_translation::kTranslateStreamingBySentence)` | Desktop |
| `trees-in-viz` | Trees in viz | `FEATURE_VALUE_TYPE(features::kTreesInViz)` | All |
| `ui-debug-tools` | Debugging tools for UI | `FEATURE_VALUE_TYPE(features::kUIDebugTools)` | Win, Linux, Mac |
| `ui-disable-partial-swap` | Partial swap | `SINGLE_DISABLE_VALUE_TYPE(switches::kUIDisablePartialSwap)` | All |
| `undo-migration-of-syncing-user-to-signed-in` | Undo the migration of syncing users to signed-in state | `FEATURE_VALUE_TYPE(switches::kUndoMigrationOfSyncingUserToSignedIn)` | Mac, Win, Linux |
| `unsafely-treat-insecure-origin-as-secure` | Insecure origins treated as secure | `ORIGIN_LIST_VALUE_TYPE(network::switches::kUnsafelyTreatInsecureOriginAsSecure, "")` | All |
| `updater-ui` | Chrome Updater UI | `FEATURE_VALUE_TYPE(features::kUpdaterUI)` | Linux, Mac, Win |
| `use-dmsaa-for-tiles` | Use DMSAA for tiles | `FEATURE_VALUE_TYPE(::features::kUseDMSAAForTiles)` | All |
| `use-out-of-process-video-decoding` | Use out-of-process video decoding (OOP-VD) | `FEATURE_VALUE_TYPE(media::kUseOutOfProcessVideoDecoding)` | Linux, CrOS |
| `use-passthrough-command-decoder` | Use passthrough command decoder | `FEATURE_VALUE_TYPE(features::kDefaultPassthroughCommandDecoder)` | All |
| `use-primary-and-tonal-buttons-for-promos` | Use primary and tonal buttons for promos | `FEATURE_VALUE_TYPE(switches::kUsePrimaryAndTonalButtonsForPromos)` | Desktop |
| `use-shared-image-in-oop-vd` | Use Shared Image in OOP-VD | `FEATURE_VALUE_TYPE(media::kUseSharedImageInOOPVDProcess)` | Linux, CrOS |
| `use-sync-sandbox` | Use Chrome Sync sandbox | `—` | All |
| `use-unexportable-key-service-in-browser-process` | Enable UnexportableKeyService mojo service in the browser process. | `FEATURE_VALUE_TYPE(network::features::kUseUnexportableKeyServiceInBrowserProcess)` | Mac, Win, Linux |
| `user-value-default-browser-strings` | Default Browser settings page - updated strings | `FEATURE_VALUE_TYPE(features::kUserValueDefaultBrowserStrings)` | Desktop |
| `variations-seed-corpus` | Variations seed corpus | `STRING_VALUE_TYPE(variations::switches::kVariationsSeedCorpus, "")` | All |
| `verify-mtcs` | Verify MTCs | `FEATURE_VALUE_TYPE(net::features::kVerifyMTCs)` | Desktop, Android |
| `verify-qwacs` | Verify QWACs | `FEATURE_VALUE_TYPE(net::features::kVerifyQWACs)` | All |
| `vertical-tabs` | Vertical Tabs | `FEATURE_VALUE_TYPE(tabs::kVerticalTabs)` | Desktop |
| `viewport-segments` | Viewport Segments API | `FEATURE_VALUE_TYPE(blink::features::kViewportSegments)` | All |
| `visited-url-ranking-service-domain-deduplication` | Visited URL ranking deduplication strategy | `FEATURE_WITH_PARAMS_VALUE_TYPE(visited_url_ranking::features::kVisitedURLRankingDeduplication, kVisitedURLRankingDomainDeduplicationVariations, "visited-url-ranking-service-domain-deduplication")` | All |
| `visited-url-ranking-service-history-visibility-score-filter` |  | `FEATURE_VALUE_TYPE(visited_url_ranking::features:: kVisitedURLRankingHistoryVisibilityScoreFilter)` | All |
| `vulkan-from-angle` | Vulkan from ANGLE | `FEATURE_VALUE_TYPE(features::kVulkanFromANGLE)` | Linux, Android |
| `wallet-service-use-sandbox` | Use Google Payments sandbox servers | `—` | Android, Desktop |
| `wallpaper-search-settings-visibility` | Wallpaper Search Settings Visibility | `FEATURE_VALUE_TYPE(optimization_guide::features::internal:: kWallpaperSearchSettingsVisibility)` | Desktop |
| `wayland-session-management` | Wayland session management | `FEATURE_VALUE_TYPE(features::kWaylandSessionManagement)` | Linux |
| `web-app-installation-api` | Web App Installation API | `FEATURE_VALUE_TYPE(blink::features::kWebAppInstallation)` | Desktop |
| `web-app-migrate-preinstalled-chat` | Migrate preinstalled Chat app | `FEATURE_VALUE_TYPE(features::kWebAppMigratePreinstalledChat)` | Desktop |
| `web-app-migration-api` | Web App Migration API | `FEATURE_VALUE_TYPE(blink::features::kWebAppMigrationApi)` | Desktop |
| `web-authentication-ambient-signin` | Enable Ambient sign-in for WebAuthn get requests | `FEATURE_VALUE_TYPE(device::kWebAuthnAmbientSignin)` | Desktop |
| `web-authentication-immediate-get` | Enable immediate mediation for WebAuthn get requests | `FEATURE_VALUE_TYPE(device::kWebAuthnImmediateGet)` | All |
| `web-authentication-permit-enterprise-attestation` | Web Authentication Enterprise Attestation | `ORIGIN_LIST_VALUE_TYPE(webauthn::switches::kPermitEnterpriseAttestationOriginList, "")` | All |
| `web-hid-in-web-view` | Web HID in WebView | `FEATURE_VALUE_TYPE(extensions_features::kEnableWebHidInWebView)` | All |
| `web-identity-digital-credentials` | DigitalCredentials | `FEATURE_WITH_PARAMS_VALUE_TYPE(features::kWebIdentityDigitalCredentials, kWebIdentityDigitalIdentityCredentialVariations, "WebIdentityDigitalCredentials")` | All |
| `web-identity-digital-credentials-creation` | DigitalCredentialsCreation | `FEATURE_VALUE_TYPE(features::kWebIdentityDigitalCredentialsCreation)` | All |
| `web-machine-learning-neural-network` | Enables WebNN API | `FEATURE_VALUE_TYPE(webnn::mojom::features::kWebMachineLearningNeuralNetwork)` | All |
| `web-request-security-info` | Enable SecurityInfo in WebRequest API | `FEATURE_VALUE_TYPE(extensions_features::kWebRequestSecurityInfo)` | Desktop |
| `web-signin-leads-to-implicitly-signed-in-state` | Web Signin leads To implicitly signed-in state | `FEATURE_VALUE_TYPE(switches::kWebSigninLeadsToImplicitlySignedInState)` | Desktop |
| `webrtc-pqc-for-dtls` | WebRTC PQC for DTLS | `FEATURE_VALUE_TYPE(blink::features::kWebRtcPqcForDtls)` | All |
| `webtransport-developer-mode` | WebTransport Developer Mode | `SINGLE_VALUE_TYPE(switches::kWebTransportDeveloperMode)` | All |
| `webui-omnibox-aim-popup` | WebUI Omnibox AIM Popup | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::internal::kWebUIOmniboxAimPopup, kWebUIOmniboxAimPopupVariations, "WebUIOmniboxAimPopupVariations")` | Desktop |
| `webui-omnibox-aim-popup-disable-animation` | WebUI Omnibox AIM Popup Disable Animation | `FEATURE_VALUE_TYPE(omnibox::kWebUIOmniboxAimPopupDisableAnimation)` | Desktop |
| `webui-omnibox-full-popup` | WebUI Omnibox Full Popup | `FEATURE_VALUE_TYPE(omnibox::kWebUIOmniboxFullPopup)` | Desktop |
| `webui-omnibox-popup` | WebUI Omnibox Popup | `FEATURE_VALUE_TYPE(omnibox::kWebUIOmniboxPopup)` | Desktop |
| `webui-omnibox-popup-debug` | WebUI Omnibox Popup Debug Mode | `FEATURE_WITH_PARAMS_VALUE_TYPE(omnibox::kWebUIOmniboxPopupDebug, kWebUIOmniboxPopupDebugVariations, "WebUIOmniboxPopupDebugVariations")` | Desktop |
| `webui-omnibox-popup-selection-control` | WebUI Omnibox Popup Selection Control | `FEATURE_VALUE_TYPE(omnibox::kWebUIOmniboxPopupSelectionControl)` | Desktop |
| `webxr-hand-anonymization` | WebXr Hand Anonymization Strategy | `MULTI_VALUE_TYPE(KWebXrHandAnonymizationChoices)` | Desktop, Android |
| `webxr-incubations` | WebXR Incubations | `FEATURE_VALUE_TYPE(device::features::kWebXRIncubations)` | All |
| `webxr-internals` | WebXR Internals Debugging Page | `FEATURE_VALUE_TYPE(device::features::kWebXrInternals)` | Desktop, Android |
| `webxr-runtime` | Force WebXr Runtime | `MULTI_VALUE_TYPE(kWebXrForceRuntimeChoices)` | Desktop, Android |
| `writer-api-for-gemini-nano` | Writer API for Gemini Nano | `FEATURE_WITH_PARAMS_VALUE_TYPE(blink::features::kAIWriterAPI, kAILangsVariation, "kAIWriterAPI")` | Desktop |
| `xslt` | XSLT | `FEATURE_VALUE_TYPE(blink::features::kXSLT)` | All |
| `«kExtensionAiDataInternalName»` | Enables AI Data collection via extension | `SINGLE_VALUE_TYPE(switches::kExtensionAiDataCollection)` | Desktop |
| `«kWebiumFlag»` | Webium | `—` | Desktop |

---

### Regenerating this file

```sh
TAG=146.0.7680.166
base=https://chromium.googlesource.com/chromium/src/+/refs/tags/$TAG
curl -s "$base/chrome/browser/about_flags.cc?format=TEXT"      | base64 -d > about_flags.cc
curl -s "$base/chrome/browser/flag_descriptions.h?format=TEXT" | base64 -d > flag_descriptions.h
# parse kFeatureEntries[] in about_flags.cc, join titles from flag_descriptions.h
```
