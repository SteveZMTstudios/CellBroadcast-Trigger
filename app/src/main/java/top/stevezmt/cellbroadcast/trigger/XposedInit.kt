package top.stevezmt.cellbroadcast.trigger

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XposedHelpers

class XposedInit : IXposedHookLoadPackage {
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
                                XposedBridge.log("CellBroadcastTrigger: Receiver registered in GMS")
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
                            }
                        }
                    }
                )
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

        // Try to find the EAlertUxArgs class with more potential packages
        val potentialPackages = listOf(
            "com.google.android.location.quake.ealert.ux.EAlertUxArgs",
            "com.google.android.location.ealert.ux.EAlertUxArgs",
            "com.google.android.gms.location.ealert.ux.EAlertUxArgs",
            "com.google.android.location.ealert.EAlertUxArgs",
            "com.google.android.gms.location.ealert.EAlertUxArgs",
            "com.google.android.gms.thunderberry.EAlertUxArgs"
        )

        var argsClass: Class<*>? = null
        for (pkg in potentialPackages) {
            try {
                argsClass = XposedHelpers.findClass(pkg, classLoader)
                XposedBridge.log("CellBroadcastTrigger: Found args class in $pkg")
                break
            } catch (e: Throwable) {
                // Continue to next
            }
        }

        if (argsClass == null) {
            XposedBridge.log("CellBroadcastTrigger: Could not find EAlertUxArgs class in any known package")
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

        // We don't know the constructor, so we'll try to instantiate and set fields
        val args = try {
            // Use Unsafe to allocate instance without constructor
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)
            val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
            val obj = allocateInstance.invoke(unsafe, argsClass)
            
            XposedBridge.log("CellBroadcastTrigger: Allocated instance via Unsafe")

            // Helper to create LatLng
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
            
            // Create polygon based on radius
            val radiusKm = triggerIntent.getDoubleExtra("polygon_radius", 50.0)
            val polygon = java.util.ArrayList<Any>()
            
            // 1 deg lat ~= 111km
            // 1 deg lng ~= 111km * cos(lat)
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
                polygon.add(p1) // Close the loop
            }

            // Map fields based on analysis of logs and types
            // a (long) -> shakeTimeEpochSeconds
            try { XposedHelpers.setLongField(obj, "a", System.currentTimeMillis() / 1000) } catch (e: Throwable) {}
            
            // b (LatLng) -> epicenter
            if (epicenter != null) try { XposedHelpers.setObjectField(obj, "b", epicenter) } catch (e: Throwable) {}
            
            // c (float) -> magnitude
            try { XposedHelpers.setFloatField(obj, "c", magnitude.toFloat()) } catch (e: Throwable) {}
            
            // d (LatLng) -> currentLocation
            if (currentLocation != null) try { XposedHelpers.setObjectField(obj, "d", currentLocation) } catch (e: Throwable) {}
            
            // e (double) -> distanceToEpicenterKm
            val distanceParam = triggerIntent.getDoubleExtra("distance", 41.2)
            try { XposedHelpers.setDoubleField(obj, "e", distanceParam) } catch (e: Throwable) {}
            
            // f (List) -> MMI5 boundary (Strong shaking)
            try { XposedHelpers.setObjectField(obj, "f", polygon) } catch (e: Throwable) {}
            
            // g (boolean) -> isTestAlert
            XposedBridge.log("CellBroadcastTrigger: Setting isTestAlert (g) to $isTest")
            try { XposedHelpers.setBooleanField(obj, "g", isTest) } catch (e: Throwable) {}
            
            // h (String) -> eventId
            try { XposedHelpers.setObjectField(obj, "h", eventId) } catch (e: Throwable) {}
            
            // i (long) -> timeIssuedMillis
            try { XposedHelpers.setLongField(obj, "i", System.currentTimeMillis()) } catch (e: Throwable) {}
            
            // j (long) -> timeoutMs
            try { XposedHelpers.setLongField(obj, "j", 180000L) } catch (e: Throwable) {}
            
            // Overrides from Intent
            val overrideK = triggerIntent.getIntExtra("override_k", -1)
            val overrideM = triggerIntent.getIntExtra("override_m", -1)
            val overrideN = triggerIntent.getIntExtra("override_n", -1)
            val regionNameParam = triggerIntent.getStringExtra("region_name")

            // k (int) -> alerUi
            // 5 is the only one known to work.
            val uiType = if (overrideK != -1) overrideK else 5
            try { XposedHelpers.setIntField(obj, "k", uiType) } catch (e: Throwable) {}
            
            // l (List) -> MMI3 boundary (Weak shaking)
            try { XposedHelpers.setObjectField(obj, "l", polygon) } catch (e: Throwable) {}
            
            // m (int) -> alertSourceId
            val sourceId = if (overrideM != -1) overrideM else 2
            try { XposedHelpers.setIntField(obj, "m", sourceId) } catch (e: Throwable) {}
            
            // n (int) -> uiMessageTypeId
            val msgType = if (overrideN != -1) overrideN else 1
            try { XposedHelpers.setIntField(obj, "n", msgType) } catch (e: Throwable) {}
            
            // o (String) -> arwRegionName
            val regionName = if (!regionNameParam.isNullOrEmpty()) regionNameParam else "San Francisco"
            try { XposedHelpers.setObjectField(obj, "o", regionName) } catch (e: Throwable) {}
            
            // p (boolean) -> earthquakeDetectionOn
            try { XposedHelpers.setBooleanField(obj, "p", true) } catch (e: Throwable) {}
            
            // q (boolean) -> ringerSetToVibrate
            try { XposedHelpers.setBooleanField(obj, "q", false) } catch (e: Throwable) {}
            
            // r (boolean) -> showMagnitude
            try { XposedHelpers.setBooleanField(obj, "r", true) } catch (e: Throwable) {}

            XposedBridge.log("CellBroadcastTrigger: Fields populated successfully")
            obj
        } catch (e: Throwable) {
            XposedBridge.log("CellBroadcastTrigger: Failed to construct args: ${e.message}")
            // Fallback to trying to find a constructor if Unsafe fails (unlikely on Android but possible)
            try {
                 XposedBridge.log("CellBroadcastTrigger: Attempting constructor fallback...")
                 val constructors = argsClass.declaredConstructors
                 for (cons in constructors) {
                     XposedBridge.log("CellBroadcastTrigger: Found constructor: ${cons}")
                 }
            } catch (e2: Throwable) {}
            null
        }

        if (args != null) {
            val intent = android.content.Intent("com.google.android.location.ealert.ux.EALERT_SAFETY_INFO")
            intent.setClassName("com.google.android.gms", "com.google.android.location.ealert.ux.EAlertSafetyInfoActivity")
            
            val uxExtra = triggerIntent.getStringExtra("ux_extra")
            if (uxExtra != null) {
                intent.putExtra("EALERT_UX_EXTRA", uxExtra)
            }
            
            intent.putExtra("EALERT_TAKE_ACTION_ARGS", args as android.os.Parcelable)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            XposedBridge.log("CellBroadcastTrigger: Started EAlertSafetyInfoActivity with custom args")
        }
    }
}
