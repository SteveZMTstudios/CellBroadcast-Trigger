package top.stevezmt.cellbroadcast.trigger

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XposedHelpers
import dalvik.system.DexFile

class XposedInit : IXposedHookLoadPackage {
    companion object {
        @Volatile private var discoveredArgsClassName: String? = null
        @Volatile private var discoveredActivityClassName: String? = null
        @Volatile private var discoveredAction: String? = null
        @Volatile private var discoveredArgsExtraKey: String? = null
        @Volatile private var discoveredIntentOperationClassName: String? = null
        @Volatile private var dexScanAttempted: Boolean = false
        @Volatile private var ealertTargetProcessName: String? = null
        private val discoveredOperationActions = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        private val registeredReceiverProcesses = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "top.stevezmt.cellbroadcast.trigger") {
            try {
                XposedHelpers.findAndHookMethod(
                    "top.stevezmt.cellbroadcast.trigger.MainActivity",
                    lpparam.classLoader,
                    "isXposedActive",
                    object : de.robv.android.xposed.XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            return true
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("CellBroadcastTrigger: Self-hook failed: ${e.message}")
            }
        }

        if (lpparam.packageName == "com.google.android.gms") {
            XposedBridge.log("CellBroadcastTrigger: Hooking GMS")
            
            // Register a receiver in GMS process to trigger alerts
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.Application",
                    lpparam.classLoader,
                    "onCreate",
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val context = param.thisObject as android.content.Context
                            val currentProcess = try {
                                android.app.Application.getProcessName()
                            } catch (_: Throwable) {
                                "unknown"
                            }

                            val targetProcess = resolveEalertTargetProcess(context)
                            val shouldRegister =
                                targetProcess == null ||
                                    targetProcess == currentProcess ||
                                    currentProcess == "com.google.android.gms" ||
                                    currentProcess.contains("persistent")

                            if (!shouldRegister) {
                                XposedBridge.log("CellBroadcastTrigger: Skip receiver register in process=$currentProcess (anchor target=$targetProcess)")
                                return
                            }

                            if (registeredReceiverProcesses.contains(currentProcess)) {
                                XposedBridge.log("CellBroadcastTrigger: Receiver already registered in process=$currentProcess")
                                return
                            }

                            val filter = android.content.IntentFilter("top.stevezmt.trigger.ACTION_REAL_ALERT")
                            
                            try {
                                @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag", "WrongConstant")
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    // Android 13+ requires RECEIVER_EXPORTED (0x2)
                                    context.registerReceiver(object : android.content.BroadcastReceiver() {
                                        override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                                            XposedBridge.log("CellBroadcastTrigger: Received trigger for real alert")
                                            try {
                                                triggerGmsAlert(ctx, lpparam.classLoader, intent)
                                            } catch (e: Exception) {
                                                XposedBridge.log("CellBroadcastTrigger: Failed to trigger: ${e.message}")
                                            }
                                        }
                                    }, filter, 2) // Context.RECEIVER_EXPORTED
                                } else {
                                    context.registerReceiver(object : android.content.BroadcastReceiver() {
                                        override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                                            XposedBridge.log("CellBroadcastTrigger: Received trigger for real alert")
                                            try {
                                                triggerGmsAlert(ctx, lpparam.classLoader, intent)
                                            } catch (e: Exception) {
                                                XposedBridge.log("CellBroadcastTrigger: Failed to trigger: ${e.message}")
                                            }
                                        }
                                    }, filter)
                                }
                                registeredReceiverProcesses.add(currentProcess)
                                XposedBridge.log("CellBroadcastTrigger: Receiver registered in GMS process=$currentProcess")
                            } catch (e: Throwable) {
                                XposedBridge.log("CellBroadcastTrigger: Failed to register receiver: ${e.message}")
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("CellBroadcastTrigger: Application hook failed: ${e.message}")
            }

            // Hook for debugging earthquake alert activities and their intents
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    lpparam.classLoader,
                    "onCreate",
                    android.os.Bundle::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as android.app.Activity
                            val className = activity.javaClass.name
                            if (className.contains("location") || className.contains("ealert") || className.contains("thunderberry")) {
                                XposedBridge.log("CellBroadcastTrigger: Activity onCreate: $className")
                                val intent = activity.intent
                                if (intent != null) {
                                    XposedBridge.log("CellBroadcastTrigger: Intent Action: ${intent.action}")
                                    XposedBridge.log("CellBroadcastTrigger: Intent Component: ${intent.component}")
                                    intent.extras?.let { extras ->
                                        for (key in extras.keySet()) {
                                            val value = extras.get(key)
                                            val className = value?.javaClass?.name ?: "null"
                                            XposedBridge.log("CellBroadcastTrigger: Extra: $key ($className) = $value")
                                        }
                                    }
                                }
                            }
                        }
                    }
                )

                // Hook startActivity to see how GMS navigates internally
                XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    lpparam.classLoader,
                    "startActivity",
                    android.content.Intent::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args[0] as android.content.Intent
                            val component = intent.component?.className ?: ""
                            if (component.contains("location") || component.contains("ealert") || component.contains("thunderberry")) {
                                XposedBridge.log("CellBroadcastTrigger: GMS starting activity: $component")
                                XposedBridge.log("CellBroadcastTrigger: Intent: $intent")

                                // Learn obfuscated entry points and extra keys at runtime.
                                discoveredActivityClassName = component
                                discoveredAction = intent.action ?: discoveredAction

                                try {
                                    val extras = intent.extras
                                    if (extras != null) {
                                        for (key in extras.keySet()) {
                                            val value = extras.get(key)
                                            if (value is android.os.Parcelable) {
                                                val valueClass = value.javaClass
                                                val hasLatLng = valueClass.declaredFields.any { f ->
                                                    f.type.name.contains("LatLng", ignoreCase = true)
                                                }
                                                val hasList = valueClass.declaredFields.any { f ->
                                                    java.util.List::class.java.isAssignableFrom(f.type)
                                                }
                                                val hasFloat = valueClass.declaredFields.any { f ->
                                                    f.type == java.lang.Float.TYPE
                                                }
                                                if (hasLatLng && hasList && hasFloat) {
                                                    discoveredArgsClassName = valueClass.name
                                                    discoveredArgsExtraKey = key
                                                    XposedBridge.log("CellBroadcastTrigger: Learned args class=${valueClass.name}, key=$key")
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    XposedBridge.log("CellBroadcastTrigger: Failed to learn extras: ${e.message}")
                                }
                            }
                        }
                    }
                )

                // Hook startService to learn IntentOperation class and action dynamically.
                XposedHelpers.findAndHookMethod(
                    "android.content.ContextWrapper",
                    lpparam.classLoader,
                    "startService",
                    android.content.Intent::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args[0] as? android.content.Intent ?: return
                            val componentName = intent.component?.className
                            val action = intent.action

                            val containsEalertAction = action?.contains("EALERT", ignoreCase = true) == true
                            val looksLikeEalertComponent = componentName?.let {
                                it.contains("ealert", ignoreCase = true) ||
                                    it.contains("location", ignoreCase = true) ||
                                    it.contains("quake", ignoreCase = true)
                            } == true

                            if (!containsEalertAction && !looksLikeEalertComponent) return

                            if (!componentName.isNullOrEmpty()) {
                                discoveredIntentOperationClassName = componentName
                            }
                            if (!action.isNullOrEmpty()) {
                                discoveredOperationActions.add(action)
                            }

                            val argsExtra = intent.getParcelableExtra<android.os.Parcelable>("EALERT_TAKE_ACTION_ARGS")
                            if (argsExtra != null) {
                                discoveredArgsClassName = argsExtra.javaClass.name
                                discoveredArgsExtraKey = "EALERT_TAKE_ACTION_ARGS"
                            }

                            XposedBridge.log(
                                "CellBroadcastTrigger: Learned service route class=${discoveredIntentOperationClassName ?: "?"} action=${action ?: "?"}"
                            )
                        }
                    }
                )

                // Hook Chimera IntentOperation entry to capture dynamic operation class/action.
                try {
                    XposedHelpers.findAndHookMethod(
                        "com.google.android.chimera.IntentOperation",
                        lpparam.classLoader,
                        "getStartIntent",
                        android.content.Context::class.java,
                        String::class.java,
                        String::class.java,
                        object : de.robv.android.xposed.XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val opClassName = param.args[1] as? String
                                val action = param.args[2] as? String
                                if (!opClassName.isNullOrEmpty()) {
                                    discoveredIntentOperationClassName = opClassName
                                }
                                if (!action.isNullOrEmpty()) {
                                    discoveredOperationActions.add(action)
                                }
                                if (!opClassName.isNullOrEmpty() || !action.isNullOrEmpty()) {
                                    XposedBridge.log("CellBroadcastTrigger: Learned IntentOperation route class=$opClassName action=$action")
                                }
                            }
                        }
                    )
                } catch (e: Throwable) {
                    XposedBridge.log("CellBroadcastTrigger: IntentOperation hook unavailable: ${e.message}")
                }
            } catch (e: Throwable) {
                XposedBridge.log("CellBroadcastTrigger: Hook failed: ${e.message}")
            }

            // Potential targets for triggering real alerts:
            // 1. com.google.android.gms.thunderberry.receiver.ThunderberryBroadcastReceiver
            // 2. com.google.android.gms.thunderberry.service.ThunderberryService
            // 3. com.google.android.location.ealert.EAlertService
        }
    }

    private fun triggerGmsAlert(context: android.content.Context, classLoader: ClassLoader, triggerIntent: android.content.Intent) {
        val isTest = triggerIntent.getBooleanExtra("is_test", true)
        val magnitude = triggerIntent.getDoubleExtra("magnitude", 5.6)
        val eventId = triggerIntent.getStringExtra("event_id") ?: "Test Ealert"
        val requestedUx = triggerIntent.getStringExtra("ux_extra")

        val argsClass = findArgsClass(context, classLoader)

        if (argsClass == null) {
            XposedBridge.log("CellBroadcastTrigger: Could not find EAlert args class (known paths + runtime learned + dex scan)")
            return
        }

        // Log all fields of the class to help with debugging
        try {
            XposedBridge.log("CellBroadcastTrigger: Inspecting ${argsClass.name} fields:")
            for (field in argsClass.declaredFields) {
                XposedBridge.log("CellBroadcastTrigger: Field: ${field.name} (${field.type.name})")
            }
        } catch (e: Throwable) {
            XposedBridge.log("CellBroadcastTrigger: Failed to inspect fields: ${e.message}")
        }

        // Build args with constructor first (robust to obfuscated field names).
        val args = try {
            fun createLatLng(lat: Double, lng: Double): Any? {
                return try {
                    val latLngClass = XposedHelpers.findClass("com.google.android.gms.maps.model.LatLng", classLoader)
                    XposedHelpers.newInstance(latLngClass, lat, lng)
                } catch (e: Throwable) {
                    XposedBridge.log("CellBroadcastTrigger: Failed to create LatLng: ${e.message}")
                    null
                }
            }

            val lat = triggerIntent.getDoubleExtra("lat", 35.195)
            val lng = triggerIntent.getDoubleExtra("lng", -119.014)
            val epicenter = createLatLng(lat, lng)
            val currentLocation = createLatLng(37.356, -122.015)
            val distanceParam = triggerIntent.getDoubleExtra("distance", 41.2)

            val radiusKm = triggerIntent.getDoubleExtra("polygon_radius", 50.0)
            val polygon = java.util.ArrayList<Any>()
            val latOffset = radiusKm / 111.0
            val lngOffset = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))
            val p1 = createLatLng(lat + latOffset, lng - lngOffset)
            val p2 = createLatLng(lat + latOffset, lng + lngOffset)
            val p3 = createLatLng(lat - latOffset, lng + lngOffset)
            val p4 = createLatLng(lat - latOffset, lng - lngOffset)
            if (p1 != null && p2 != null && p3 != null && p4 != null) {
                polygon.add(p1)
                polygon.add(p2)
                polygon.add(p3)
                polygon.add(p4)
                polygon.add(p1)
            }

            val overrideK = triggerIntent.getIntExtra("override_k", -1)
            val overrideM = triggerIntent.getIntExtra("override_m", -1)
            val overrideN = triggerIntent.getIntExtra("override_n", -1)
            val regionNameParam = triggerIntent.getStringExtra("region_name")

            // Newer GMS uses uiType==6 for EALERT_DISPLAY; demo uses 5.
            val defaultUiType = when (requestedUx) {
                "EALERT_DISPLAY" -> 6
                "EALERT_DEMO" -> 5
                else -> if (isTest) 5 else 6
            }
            val uiType = when {
                overrideK == -1 -> defaultUiType
                requestedUx == "EALERT_DISPLAY" && overrideK == 5 -> {
                    XposedBridge.log("CellBroadcastTrigger: override_k=5 incompatible with EALERT_DISPLAY, forcing uiType=6")
                    6
                }
                else -> overrideK
            }
            val sourceId = if (overrideM != -1) overrideM else 2
            val msgType = if (overrideN != -1) overrideN else 1
            val regionName = if (!regionNameParam.isNullOrEmpty()) regionNameParam else "San Francisco"

            val ctor = argsClass.declaredConstructors.firstOrNull { c -> c.parameterTypes.size == 21 }
            if (ctor != null) {
                ctor.isAccessible = true
                val instance = ctor.newInstance(
                    System.currentTimeMillis() / 1000,  // shakeTimeEpochSeconds
                    epicenter,                           // epicenter
                    magnitude.toFloat(),                 // magnitude
                    currentLocation,                     // current location
                    distanceParam,                       // distance
                    isTest,                              // isTestAlert
                    eventId,                             // eventId
                    System.currentTimeMillis(),          // timeIssuedMillis
                    180000L,                             // timeoutMs
                    uiType,                              // alert UI type
                    sourceId,                            // source id
                    msgType,                             // message type id
                    regionName,                          // region
                    true,                                // earthquakeDetectionOn
                    false,                               // ringerSetToVibrate
                    true,                                // showMagnitude
                    polygon,                             // MMI3
                    polygon,                             // MMI4
                    polygon,                             // MMI5
                    true,                                // useSound
                    true                                 // useVibration
                )
                XposedBridge.log("CellBroadcastTrigger: Constructed args via 21-arg constructor, uiType=$uiType")
                instance
            } else {
                XposedBridge.log("CellBroadcastTrigger: 21-arg constructor not found, fallback to Unsafe field mapping")
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
                theUnsafeField.isAccessible = true
                val unsafe = theUnsafeField.get(null)
                val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
                val obj = allocateInstance.invoke(unsafe, argsClass)

                // Legacy field mapping (older builds where fields were a..r style).
                try { XposedHelpers.setLongField(obj, "a", System.currentTimeMillis() / 1000) } catch (_: Throwable) {}
                if (epicenter != null) try { XposedHelpers.setObjectField(obj, "b", epicenter) } catch (_: Throwable) {}
                try { XposedHelpers.setFloatField(obj, "c", magnitude.toFloat()) } catch (_: Throwable) {}
                if (currentLocation != null) try { XposedHelpers.setObjectField(obj, "d", currentLocation) } catch (_: Throwable) {}
                try { XposedHelpers.setDoubleField(obj, "e", distanceParam) } catch (_: Throwable) {}
                try { XposedHelpers.setBooleanField(obj, "f", isTest) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(obj, "g", eventId) } catch (_: Throwable) {}
                try { XposedHelpers.setLongField(obj, "h", System.currentTimeMillis()) } catch (_: Throwable) {}
                try { XposedHelpers.setLongField(obj, "i", 180000L) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(obj, "j", uiType) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(obj, "k", sourceId) } catch (_: Throwable) {}
                try { XposedHelpers.setIntField(obj, "l", msgType) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(obj, "m", regionName) } catch (_: Throwable) {}
                try { XposedHelpers.setBooleanField(obj, "n", true) } catch (_: Throwable) {}
                try { XposedHelpers.setBooleanField(obj, "o", false) } catch (_: Throwable) {}
                try { XposedHelpers.setBooleanField(obj, "p", true) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(obj, "q", polygon) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(obj, "r", polygon) } catch (_: Throwable) {}
                try { XposedHelpers.setObjectField(obj, "s", polygon) } catch (_: Throwable) {}
                try { XposedHelpers.setBooleanField(obj, "t", true) } catch (_: Throwable) {}
                try { XposedHelpers.setBooleanField(obj, "u", true) } catch (_: Throwable) {}
                obj
            }
        } catch (e: Throwable) {
            XposedBridge.log("CellBroadcastTrigger: Failed to construct args: ${e.message}")
            null
        }

        if (args != null) {
            val uxExtra = requestedUx
            val started = startEalertActivity(context, args as android.os.Parcelable, uxExtra)
            if (!started) {
                XposedBridge.log("CellBroadcastTrigger: Failed to launch EAlert activity with discovered strategies")
            }
        }
    }

    private fun findArgsClass(context: android.content.Context, classLoader: ClassLoader): Class<*>? {
        // 1) Runtime learned class from real GMS navigation/service path.
        discoveredArgsClassName?.let { learned ->
            try {
                val clazz = XposedHelpers.findClass(learned, classLoader)
                XposedBridge.log("CellBroadcastTrigger: Found args class via runtime learning: $learned")
                return clazz
            } catch (_: Throwable) {
            }
        }

        // 2) Dex scan heuristic for obfuscated builds.
        if (!dexScanAttempted) {
            dexScanAttempted = true
            try {
                val dexFile = DexFile(context.packageCodePath)
                val entries = dexFile.entries()
                var scanned = 0
                while (entries.hasMoreElements()) {
                    val className = entries.nextElement()
                    scanned++
                    if (!(className.contains("ealert", true)
                                || className.contains("quake", true)
                                || className.contains("thunderberry", true)
                                || className.contains("location", true))) {
                        continue
                    }

                    try {
                        val clazz = Class.forName(className, false, classLoader)
                        if (!android.os.Parcelable::class.java.isAssignableFrom(clazz)) continue

                        val fields = clazz.declaredFields
                        if (fields.size !in 8..40) continue

                        val hasLatLng = fields.any { it.type.name.contains("LatLng", ignoreCase = true) }
                        val hasList = fields.any { java.util.List::class.java.isAssignableFrom(it.type) }
                        val hasFloat = fields.any { it.type == java.lang.Float.TYPE }
                        val hasDouble = fields.any { it.type == java.lang.Double.TYPE }
                        val hasString = fields.any { it.type == String::class.java }
                        val hasInt = fields.any { it.type == java.lang.Integer.TYPE }

                        if (hasLatLng && hasList && hasFloat && hasDouble && hasString && hasInt) {
                            discoveredArgsClassName = className
                            XposedBridge.log("CellBroadcastTrigger: Found args class via dex heuristic: $className (scanned=$scanned)")
                            return clazz
                        }
                    } catch (_: Throwable) {
                    }
                }
                XposedBridge.log("CellBroadcastTrigger: Dex heuristic scan completed; no args class found")
            } catch (e: Throwable) {
                XposedBridge.log("CellBroadcastTrigger: Dex scan failed: ${e.message}")
            }
        }

        // 3) Final compatibility fallback for old/non-obfuscated builds.
        val potentialPackages = listOf(
            "com.google.android.location.quake.ealert.p174ux.EAlertUxArgs",
            "com.google.android.location.quake.ealert.ux.EAlertUxArgs",
            "com.google.android.location.ealert.ux.EAlertUxArgs",
            "com.google.android.gms.location.ealert.ux.EAlertUxArgs",
            "com.google.android.location.ealert.EAlertUxArgs",
            "com.google.android.gms.location.ealert.EAlertUxArgs",
            "com.google.android.gms.thunderberry.EAlertUxArgs"
        )

        for (pkg in potentialPackages) {
            try {
                val clazz = XposedHelpers.findClass(pkg, classLoader)
                discoveredArgsClassName = clazz.name
                XposedBridge.log("CellBroadcastTrigger: Found args class in compatibility fallback: $pkg")
                return clazz
            } catch (_: Throwable) {
            }
        }

        return null
    }

    private fun startEalertActivity(
        context: android.content.Context,
        args: android.os.Parcelable,
        uxExtra: String?
    ): Boolean {
        // Strategy 0: use GMS internal IntentOperation pipeline first.
        // This follows the same path used by GMS debug menu and is less likely to show blank activity.
        if (startEalertViaIntentOperation(context, args, uxExtra)) {
            return true
        }

        val actionCandidates = listOfNotNull(
            discoveredOperationActions.firstOrNull { it.contains("SAFETY_INFO") },
            discoveredAction,
            "com.google.android.location.ealert.ux.EALERT_SAFETY_INFO"
        ).distinct()

        val componentCandidates = listOfNotNull(
            discoveredActivityClassName,
            "com.google.android.location.ealert.p171ux.EAlertSafetyInfoActivity",
            "com.google.android.location.ealert.ux.EAlertSafetyInfoActivity"
        ).distinct()

        val argsKeyCandidates = listOfNotNull(
            discoveredArgsExtraKey,
            "EALERT_TAKE_ACTION_ARGS"
        ).distinct()

        fun buildIntent(action: String?, componentClassName: String?): android.content.Intent {
            val intent = if (action != null) android.content.Intent(action) else android.content.Intent()
            intent.setPackage("com.google.android.gms")
            if (componentClassName != null) {
                intent.setClassName("com.google.android.gms", componentClassName)
            }
            if (uxExtra != null) {
                intent.putExtra("EALERT_UX_EXTRA", uxExtra)
            }
            for (key in argsKeyCandidates) {
                intent.putExtra(key, args)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        // Strategy A0: explicit activity without action (works on some obfuscated builds).
        for (component in componentCandidates) {
            try {
                val intent = buildIntent(null, component)
                context.startActivity(intent)
                XposedBridge.log("CellBroadcastTrigger: Started EAlert activity via explicit component only=$component")
                return true
            } catch (_: Throwable) {
            }
        }

        // Strategy A: explicit component + action.
        for (component in componentCandidates) {
            for (action in actionCandidates) {
                try {
                    val intent = buildIntent(action, component)
                    context.startActivity(intent)
                    XposedBridge.log("CellBroadcastTrigger: Started EAlert activity via explicit component=$component action=$action")
                    return true
                } catch (e: Throwable) {
                    XposedBridge.log("CellBroadcastTrigger: Explicit launch failed component=$component action=$action error=${e.message}")
                }
            }
        }

        // Strategy B: resolve implicit handlers dynamically in current GMS build
        for (action in actionCandidates) {
            try {
                val probe = android.content.Intent(action)
                probe.`package` = "com.google.android.gms"
                val resolved = context.packageManager.queryIntentActivities(probe, 0)
                for (info in resolved) {
                    val activityName = info.activityInfo?.name ?: continue
                    try {
                        val intent = buildIntent(action, activityName)
                        context.startActivity(intent)
                        discoveredActivityClassName = activityName
                        discoveredAction = action
                        XposedBridge.log("CellBroadcastTrigger: Started EAlert activity via resolve activity=$activityName action=$action")
                        return true
                    } catch (_: Throwable) {
                    }
                }
            } catch (e: Throwable) {
                XposedBridge.log("CellBroadcastTrigger: Resolve launch failed for action=$action error=${e.message}")
            }
        }

        return false
    }

    private fun startEalertViaIntentOperation(
        context: android.content.Context,
        args: android.os.Parcelable,
        uxExtra: String?
    ): Boolean {
        val opClassCandidates = java.util.LinkedHashSet<String>()
        discoveredIntentOperationClassName?.let { opClassCandidates.add(it) }
        opClassCandidates.add("com.google.android.location.settings.EAlertGoogleSettingAlertIntentOperation")

        // Dynamic-first actions learned from real GMS calls.
        val actionCandidates = java.util.LinkedHashSet<String>()
        for (a in discoveredOperationActions) {
            if (a.contains("EALERT", ignoreCase = true)) {
                actionCandidates.add(a)
            }
        }
        actionCandidates.add("com.google.android.gms.location.EALERT_GOOGLE_SETTING_ALERT_DEMO")
        actionCandidates.add("com.google.android.gms.location.EALERT_GOOGLE_SETTING_DEBUG")

        val preferredActions = if (uxExtra == "EALERT_DEMO") {
            actionCandidates.toList()
        } else {
            // DISPLAY/debug should not fallback to DEMO action, otherwise it can open wrong/blank UI.
            actionCandidates.filter { !it.contains("DEMO", ignoreCase = true) }
        }

        for (opClassName in opClassCandidates) {
            for (action in preferredActions) {
                try {
                    val intentOpClass = XposedHelpers.findClass("com.google.android.chimera.IntentOperation", context.classLoader)
                    val getStartIntent = intentOpClass.getMethod(
                        "getStartIntent",
                        android.content.Context::class.java,
                        String::class.java,
                        String::class.java
                    )
                    val intent = getStartIntent.invoke(null, context, opClassName, action) as? android.content.Intent
                    if (intent == null) {
                        XposedBridge.log("CellBroadcastTrigger: IntentOperation.getStartIntent returned null for class=$opClassName action=$action")
                        continue
                    }

                    intent.putExtra("EALERT_TAKE_ACTION_ARGS", args)
                    if (uxExtra != null) {
                        intent.putExtra("EALERT_UX_EXTRA", uxExtra)
                    }
                    intent.setPackage("com.google.android.gms")
                    context.startService(intent)
                    discoveredIntentOperationClassName = opClassName
                    discoveredOperationActions.add(action)
                    XposedBridge.log("CellBroadcastTrigger: Started EAlert via IntentOperation class=$opClassName action=$action")
                    return true
                } catch (e: Throwable) {
                    XposedBridge.log("CellBroadcastTrigger: IntentOperation launch failed class=$opClassName action=$action error=${e.message}")
                }
            }
        }

        return false
    }

    private fun resolveEalertTargetProcess(context: android.content.Context): String? {
        ealertTargetProcessName?.let { return it }

        val actionCandidates = listOf(
            "com.google.android.location.ealert.ux.EALERT_SAFETY_INFO"
        )

        for (action in actionCandidates) {
            try {
                val probe = android.content.Intent(action).setPackage("com.google.android.gms")
                val resolved = context.packageManager.queryIntentActivities(probe, 0)
                if (resolved.isNotEmpty()) {
                    val info = resolved[0].activityInfo
                    val process = info?.processName
                    val activityName = info?.name
                    if (!process.isNullOrEmpty()) {
                        ealertTargetProcessName = process
                        if (!activityName.isNullOrEmpty()) {
                            discoveredActivityClassName = activityName
                        }
                        discoveredAction = action
                        XposedBridge.log("CellBroadcastTrigger: Resolved EAlert anchor action=$action activity=$activityName process=$process")
                        return process
                    }
                }
            } catch (e: Throwable) {
                XposedBridge.log("CellBroadcastTrigger: Failed resolving EAlert process anchor: ${e.message}")
            }
        }

        return null
    }
}
