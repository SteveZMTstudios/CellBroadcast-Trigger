package top.stevezmt.cellbroadcast.trigger;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import android.util.Base64;

public class RootMain {

    public static void main(String[] args) {
        // Initialize Looper for the main thread to allow Handler creation
        android.os.Looper.prepare();

        System.out.println("RootMain started with args: " + java.util.Arrays.toString(args));

        // Diagnostic: Print constructors for SmsCbMessage
        try {
            printConstructors("android.telephony.SmsCbMessage");
            printConstructors("android.telephony.SmsCbLocation");
            printConstructors("android.telephony.SmsCbCmasInfo");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (args.length < 3) {
            System.err.println("Usage: RootMain <base64_body_text> <cmas_class_int> <delay_ms> [is_etws]");
            return;
        }

        String body;
        try {
            // Try to decode as Base64 first
            byte[] decodedBytes = Base64.decode(args[0], Base64.DEFAULT);
            body = new String(decodedBytes, "UTF-8");
            System.out.println("Decoded Base64 body: " + body);
        } catch (Exception e) {
            // Fallback to raw string if decoding fails (for backward compatibility or manual testing)
            System.out.println("Failed to decode Base64, using raw string: " + e.getMessage());
            body = args[0];
        }

        int cmasClass = Integer.parseInt(args[1]);
        long delayMs = Long.parseLong(args[2]);
        boolean isEtws = args.length > 3 && Boolean.parseBoolean(args[3]);

        if (delayMs > 0) {
            System.out.println("Waiting for " + delayMs + " ms...");
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            System.out.println("Acquiring System Context...");
            Context context = getSystemContext();
            System.out.println("Context acquired: " + context);

            int[] subIds = getActiveSubscriptionIds(context);
            if (subIds == null || subIds.length == 0) {
                System.out.println("No active subscriptions found. Using default subId.");
                subIds = new int[]{getDefaultSubscriptionId()};
            }

            for (int subId : subIds) {
                System.out.println("Broadcasting for subId: " + subId);
                Object message;
                if (isEtws) {
                    // When isEtws is true, cmasClass argument is treated as etwsType
                    message = createEtwsMessage(body, cmasClass, subId);
                } else {
                    message = createSmsCbMessage(body, cmasClass, subId);
                }
                
                Intent intent = new Intent("android.provider.Telephony.SMS_CB_RECEIVED");
                intent.putExtra("message", (Parcelable) message);
                intent.addFlags(0x01000000); // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                
                // 1. Send standard ordered broadcast
                context.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS");
                System.out.println(" - Sent standard ordered broadcast");

                // 2. Try explicit package targeting for known receivers
                String[] packages = {
                    "com.android.cellbroadcastreceiver",
                    "com.android.cellbroadcastreceiver.module",
                    "com.google.android.cellbroadcastreceiver"
                };

                for (String pkg : packages) {
                    try {
                        Intent explicitIntent = new Intent(intent);
                        explicitIntent.setPackage(pkg);
                        context.sendOrderedBroadcast(explicitIntent, "android.permission.RECEIVE_SMS");
                        System.out.println(" - Sent explicit broadcast to " + pkg);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error occurred:");
            e.printStackTrace();
        }
    }

    private static int[] getActiveSubscriptionIds(Context context) {
        try {
            Class<?> subMgrClass = Class.forName("android.telephony.SubscriptionManager");
            Method from = subMgrClass.getMethod("from", Context.class);
            Object subMgr = from.invoke(null, context);
            
            Method getActiveSubInfoList = subMgrClass.getMethod("getActiveSubscriptionIdList");
            return (int[]) getActiveSubInfoList.invoke(subMgr);
        } catch (Exception e) {
            System.out.println("Could not get active subIds: " + e);
            return null;
        }
    }


    private static void printConstructors(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            System.out.println("Constructors for " + className + ":");
            for (Constructor<?> c : clazz.getConstructors()) {
                System.out.println(" - " + c);
            }
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                System.out.println(" - (Declared) " + c);
            }
        } catch (Exception e) {
            System.out.println("Could not inspect " + className + ": " + e);
        }
    }

    private static Context getSystemContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThreadClass.getMethod("systemMain");
        Object activityThread = systemMain.invoke(null);
        Method getSystemContext = activityThreadClass.getMethod("getSystemContext");
        return (Context) getSystemContext.invoke(activityThread);
    }

    private static int getDefaultSubscriptionId() {
        try {
            Class<?> subMgrClass = Class.forName("android.telephony.SubscriptionManager");
            Method getDefaultSubId = subMgrClass.getMethod("getDefaultSubscriptionId");
            return (int) getDefaultSubId.invoke(null);
        } catch (Exception e) {
            System.err.println("Failed to get default subId: " + e);
            return 1; // Fallback
        }
    }

    private static Object createSmsCbMessage(String body, int cmasMessageClass, int subId) throws Exception {
        // 1. Create SmsCbLocation
        Class<?> locClass = Class.forName("android.telephony.SmsCbLocation");
        Object location;
        try {
            Constructor<?> locConstructor = locClass.getConstructor(String.class, int.class, int.class);
            location = locConstructor.newInstance("", -1, -1);
        } catch (NoSuchMethodException e) {
             // Fallback for very old versions if needed, or just try default
             location = locClass.newInstance();
        }

        // 2. Create SmsCbCmasInfo
        Class<?> cmasClass = Class.forName("android.telephony.SmsCbCmasInfo");
        Constructor<?> cmasConstructor = cmasClass.getConstructor(
                int.class, int.class, int.class, int.class, int.class, int.class);
        
        // CMAS Constants - Map based on cmasMessageClass
        // 0x00: Presidential
        // 0x01: Extreme
        // 0x02: Severe
        // 0x03: Amber
        // 0x04: Test
        
        int category = 0; // CMAS_CATEGORY_GEO
        int responseType = 0; // CMAS_RESPONSE_TYPE_PREPARE
        int severity = 0; // CMAS_SEVERITY_EXTREME
        int urgency = 0; // CMAS_URGENCY_IMMEDIATE
        int certainty = 0; // CMAS_CERTAINTY_OBSERVED
        int serviceCategory = 4370; // CMAS Presidential

        switch (cmasMessageClass) {
            case 0x00: // Presidential
                serviceCategory = 4370;
                severity = 0; // Extreme
                urgency = 0; // Immediate
                break;
            case 0x01: // Extreme
                serviceCategory = 4371; // CMAS Extreme
                severity = 0; // Extreme
                urgency = 0; // Immediate
                break;
            case 0x02: // Severe
                serviceCategory = 4373; // CMAS Severe
                severity = 1; // Severe
                urgency = 1; // Expected
                break;
            case 0x03: // Amber
                serviceCategory = 4379; // CMAS Amber
                category = 1; // CMAS_CATEGORY_CBRNE (Often used for Amber? Or just rely on service category)
                // Actually Amber is usually 4379
                severity = 1; // Severe
                urgency = 1; // Expected
                break;
            case 0x04: // Test
                serviceCategory = 4383; // CMAS Test
                severity = 1;
                urgency = 1;
                break;
        }
        
        Object cmasInfo = cmasConstructor.newInstance(cmasMessageClass, category, responseType, severity, urgency, certainty);

        // 3. Create SmsCbMessage
        Class<?> msgClass = Class.forName("android.telephony.SmsCbMessage");
        
        System.out.println("Creating CMAS message for Subscription ID: " + subId + ", Category: " + serviceCategory);

        try {
            // Try newer constructor (Android 12+)
            Constructor<?> msgConstructor = msgClass.getConstructor(
                    int.class, int.class, int.class, locClass,
                    int.class, String.class, int.class, String.class,
                    int.class, Class.forName("android.telephony.SmsCbEtwsInfo"), cmasClass,
                    int.class, List.class, long.class, int.class, int.class
            );

            return msgConstructor.newInstance(
                    1, // MESSAGE_FORMAT_3GPP
                    3, // GEOGRAPHICAL_SCOPE_CELL_WIDE
                    1234, // Serial Number
                    location,
                    serviceCategory, 
                    "en",
                    0, // Data Coding Scheme (7-bit)
                    body,
                    3, // MESSAGE_PRIORITY_EMERGENCY
                    null, // No ETWS info
                    cmasInfo,
                    0, // maximumWaitTimeSec
                    null, // geometries
                    System.currentTimeMillis(), // receivedTimeMillis
                    0, // Slot 0
                    subId // SubId
            );
        } catch (NoSuchMethodException e) {
            // Fallback to older constructor (Android 6.0 - 11)
            System.out.println("Newer constructor not found, trying legacy constructor...");
            try {
                Class<?> etwsClass = Class.forName("android.telephony.SmsCbEtwsInfo");
                Constructor<?> msgConstructor = msgClass.getConstructor(
                        int.class, int.class, int.class, locClass,
                        int.class, String.class, String.class,
                        int.class, etwsClass, cmasClass
                );

                return msgConstructor.newInstance(
                        1, // MESSAGE_FORMAT_3GPP
                        3, // GEOGRAPHICAL_SCOPE_CELL_WIDE
                        1234, // Serial Number
                        location,
                        serviceCategory, 
                        "en",
                        body,
                        3, // MESSAGE_PRIORITY_EMERGENCY
                        null, // No ETWS info
                        cmasInfo
                );
            } catch (Exception ex) {
                System.out.println("Legacy constructor failed: " + ex);
                ex.printStackTrace();
                throw ex;
            }
        }
    }

    private static Object createEtwsMessage(String body, int etwsType, int subId) throws Exception {
        // 1. Create SmsCbLocation
        Class<?> locClass = Class.forName("android.telephony.SmsCbLocation");
        Object location;
        try {
            Constructor<?> locConstructor = locClass.getConstructor(String.class, int.class, int.class);
            location = locConstructor.newInstance("", -1, -1);
        } catch (NoSuchMethodException e) {
             location = locClass.newInstance();
        }

        // 2. Create SmsCbEtwsInfo
        Class<?> etwsClass = Class.forName("android.telephony.SmsCbEtwsInfo");
        // Constructor: int warningType, boolean emergencyUserAlert, boolean activatePopup, boolean primary, byte[] warningSecurityInformation
        Constructor<?> etwsConstructor = etwsClass.getConstructor(
                int.class, boolean.class, boolean.class, boolean.class, byte[].class);
        
        // ETWS Constants
        int warningType = 0; // ETWS_WARNING_TYPE_EARTHQUAKE
        int serviceCategory = 4352; // ETWS Earthquake

        switch (etwsType) {
            case 0: // Earthquake
                warningType = 0; 
                serviceCategory = 4352;
                break;
            case 1: // Tsunami
                warningType = 1;
                serviceCategory = 4353;
                break;
            case 2: // Earthquake + Tsunami
                warningType = 2;
                serviceCategory = 4354;
                break;
            case 3: // Test
                warningType = 3;
                serviceCategory = 4355;
                break;
            case 4: // Other
                warningType = 4;
                serviceCategory = 4356;
                break;
        }

        boolean emergencyUserAlert = true;
        boolean activatePopup = true;
        boolean primary = true;
        
        Object etwsInfo = etwsConstructor.newInstance(warningType, emergencyUserAlert, activatePopup, primary, null);

        // 3. Create SmsCbMessage
        Class<?> msgClass = Class.forName("android.telephony.SmsCbMessage");
        
        System.out.println("Creating ETWS message for Subscription ID: " + subId + ", Type: " + etwsType);

        try {
            // Try newer constructor (Android 12+)
            Constructor<?> msgConstructor = msgClass.getConstructor(
                    int.class, int.class, int.class, locClass,
                    int.class, String.class, int.class, String.class,
                    int.class, etwsClass, Class.forName("android.telephony.SmsCbCmasInfo"),
                    int.class, List.class, long.class, int.class, int.class
            );

            return msgConstructor.newInstance(
                    1, // MESSAGE_FORMAT_3GPP
                    3, // GEOGRAPHICAL_SCOPE_CELL_WIDE
                    1234, // Serial Number
                    location,
                    serviceCategory, 
                    "en",
                    0, // Data Coding Scheme (7-bit)
                    body,
                    3, // MESSAGE_PRIORITY_EMERGENCY
                    etwsInfo,
                    null, // No CMAS info
                    0, // maximumWaitTimeSec
                    null, // geometries
                    System.currentTimeMillis(), // receivedTimeMillis
                    0, // Slot 0
                    subId // SubId
            );
        } catch (NoSuchMethodException e) {
            // Fallback to older constructor (Android 6.0 - 11)
            System.out.println("Newer constructor not found, trying legacy constructor...");
            try {
                Constructor<?> msgConstructor = msgClass.getConstructor(
                        int.class, int.class, int.class, locClass,
                        int.class, String.class, String.class,
                        int.class, etwsClass, Class.forName("android.telephony.SmsCbCmasInfo")
                );

                return msgConstructor.newInstance(
                        1, // MESSAGE_FORMAT_3GPP
                        3, // GEOGRAPHICAL_SCOPE_CELL_WIDE
                        1234, // Serial Number
                        location,
                        serviceCategory, 
                        "en",
                        body,
                        3, // MESSAGE_PRIORITY_EMERGENCY
                        etwsInfo,
                        null // No CMAS info
                );
            } catch (Exception ex) {
                System.out.println("Legacy constructor failed: " + ex);
                ex.printStackTrace();
                throw ex;
            }
        }
    }
}

