package edu.cmu.hcii.sugilite;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import edu.cmu.hcii.sugilite.communication.SugiliteCommunicationController;
import edu.cmu.hcii.sugilite.communication.SugiliteEventBroadcastingActivity;
import edu.cmu.hcii.sugilite.dao.SugiliteAppVocabularyDao;
import edu.cmu.hcii.sugilite.ontology.SerializableUISnapshot;
import edu.cmu.hcii.sugilite.ontology.UISnapshot;
import edu.cmu.hcii.sugilite.ontology.helper.annotator.SugiliteTextAnnotator;
import edu.cmu.hcii.sugilite.recording.SugiliteScreenshotManager;
import edu.cmu.hcii.sugilite.model.AccessibilityNodeInfoList;
import edu.cmu.hcii.sugilite.automation.*;
import edu.cmu.hcii.sugilite.model.block.util.SerializableNodeInfo;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.SugiliteBlock;
import edu.cmu.hcii.sugilite.model.block.operation.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.trigger.SugiliteTriggerHandler;
import edu.cmu.hcii.sugilite.recording.TextChangedEventHandler;
import edu.cmu.hcii.sugilite.recording.newrecording.NewDemonstrationHandler;
import edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay.FullScreenRecordingOverlayManager;
import edu.cmu.hcii.sugilite.tracking.SugiliteTrackingHandler;
import edu.cmu.hcii.sugilite.ui.StatusIconManager;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.VerbalInstructionIconManager;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.study.SugiliteStudyHandler;

import static edu.cmu.hcii.sugilite.Const.BROADCASTING_ACCESSIBILITY_EVENT;
import static edu.cmu.hcii.sugilite.Const.BUILDING_VOCAB;
import static edu.cmu.hcii.sugilite.Const.HOME_SCREEN_PACKAGE_NAMES;
import static edu.cmu.hcii.sugilite.Const.KEEP_ALL_TEXT_LABEL_LIST;

public class SugiliteAccessibilityService extends AccessibilityService {
    protected WindowManager windowManager;
    protected SharedPreferences sharedPreferences;
    protected Automator automator;
    protected SugiliteData sugiliteData;
    protected StatusIconManager statusIconManager;
    protected VerbalInstructionIconManager verbalInstructionIconManager;
    protected SugiliteScreenshotManager screenshotManager;
    protected SugiliteStudyHandler sugiliteStudyHandler;
    protected Set<Integer> accessibilityEventSetToHandle, accessibilityEventSetToSend, accessibilityEventSetToTrack;
    protected Thread automatorThread;
    protected SugiliteAccessibilityService context;
    protected SugiliteTrackingHandler sugilteTrackingHandler;
    protected SugiliteAppVocabularyDao vocabularyDao;
    protected Handler handler;
    protected static final String TAG = SugiliteAccessibilityService.class.getSimpleName();
    protected SugiliteTriggerHandler triggerHandler;
    protected TextChangedEventHandler textChangedEventHandler;
    protected String lastPackageName = "";
    private NewDemonstrationHandler newDemonstrationHandler;
    private static Set<String> homeScreenPackageNameSet;
    private SugiliteTextAnnotator sugiliteTextAnnotator;
    private FullScreenRecordingOverlayManager recordingOverlayManager;
    private TextToSpeech tts;
    ExecutorService executor = Executors.newFixedThreadPool(10);

    //this thread is for generating ui snapshots
    ExecutorService uiSnapshotGenerationExecutor;


    public SugiliteAccessibilityService() {
        Log.d( TAG, "inside constructor");
    }

    private int counter = 0;

    @Override
    public void onCreate(){
        Log.d( TAG, "inside onCreate");
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        //Oscar: we need to do this validation in order to avoid Cast Exception when calling Sugilite from Middleware
        if( getApplication() instanceof  SugiliteData ) {
            sugiliteData = (SugiliteData) getApplication();
        }
        AccessibilityManager accessibilityManager = (AccessibilityManager) this.getSystemService(Context.ACCESSIBILITY_SERVICE);
        context = this;
        this.tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
            }
        });
        tts.setLanguage(Locale.US);
        sugiliteData.setTTS(tts);
        sugiliteStudyHandler = new SugiliteStudyHandler(context, LayoutInflater.from(getApplicationContext()), this, tts);
        statusIconManager = new StatusIconManager(this, sugiliteData, sharedPreferences, accessibilityManager);
        sugiliteData.statusIconManager = statusIconManager;
        recordingOverlayManager = new FullScreenRecordingOverlayManager(context, sugiliteData, sharedPreferences, this, tts);
        verbalInstructionIconManager = new VerbalInstructionIconManager(this, sugiliteStudyHandler, sugiliteData, sharedPreferences, recordingOverlayManager, this, tts);
        sugiliteData.verbalInstructionIconManager = verbalInstructionIconManager;
        statusIconManager.setVerbalInstructionIconManager(verbalInstructionIconManager);
        newDemonstrationHandler = new NewDemonstrationHandler(sugiliteData, this, LayoutInflater.from(getApplicationContext()), sharedPreferences, this);

        screenshotManager = new SugiliteScreenshotManager(sharedPreferences, getApplicationContext());
        sugiliteTextAnnotator = new SugiliteTextAnnotator(true);
        automator = new Automator(sugiliteData, this, statusIconManager, sharedPreferences, sugiliteTextAnnotator);
        sugilteTrackingHandler = new SugiliteTrackingHandler(sugiliteData, getApplicationContext());
        availableAlternatives = new HashSet<>();
        availableAlternativeNodes = new HashSet<>();
        trackingPackageVocabs = new HashSet<>();
        packageVocabs = new HashSet<>();
        vocabularyDao = new SugiliteAppVocabularyDao(getApplicationContext());

        triggerHandler = new SugiliteTriggerHandler(context, sugiliteData, sharedPreferences);
        textChangedEventHandler = new TextChangedEventHandler(sugiliteData, context, sharedPreferences, handler);

        handler = new Handler();
        homeScreenPackageNameSet = new HashSet<>();
        homeScreenPackageNameSet.addAll(Arrays.asList(HOME_SCREEN_PACKAGE_NAMES));


        SynchronousQueue<Runnable> threadPoolQueue = new SynchronousQueue<>();
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        uiSnapshotGenerationExecutor = new ThreadPoolExecutor(0, 5, 60L, TimeUnit.SECONDS, threadPoolQueue, threadFactory);

        try {
            //TODO: periodically check the status of communication controller
            sugiliteData.communicationController = SugiliteCommunicationController.getInstance(
                    getApplicationContext(), sugiliteData, sharedPreferences);
        }
        catch (Exception e){
            e.printStackTrace();
        }

        Integer[] accessibilityEventArrayToHandle = {AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_ANNOUNCEMENT};
        Integer[] accessiblityEventArrayToSend = {AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED};
        Integer[] accessibilityEventArrayToTrack = {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        };
        accessibilityEventSetToHandle = new HashSet<>(Arrays.asList(accessibilityEventArrayToHandle));
        accessibilityEventSetToSend = new HashSet<>(Arrays.asList(accessiblityEventArrayToSend));
        accessibilityEventSetToTrack = new HashSet<>(Arrays.asList(accessibilityEventArrayToTrack));

        //end recording

        //set default value for the settings
        SharedPreferences.Editor prefEditor = sharedPreferences.edit();
        prefEditor.putBoolean("recording_in_process", false);
        prefEditor.putBoolean("root_enabled", true);
        prefEditor.putBoolean("auto_fill_enabled", true);
        prefEditor.apply();

        sugiliteData.clearInstructionQueue();
        if(sugiliteData.errorHandler == null){
            sugiliteData.errorHandler = new ErrorHandler(this, sugiliteData, sharedPreferences);
        }
        if(sugiliteData.trackingName != null && sugiliteData.trackingName.contentEquals("default")){
            sugiliteData.initiateTracking(sugilteTrackingHandler.getDefaultTrackingName());
        }

        try {
            Toast.makeText(this, "Sugilite Accessibility Service Started", Toast.LENGTH_SHORT).show();
            statusIconManager.addStatusIcon();
        }
        catch (Exception e){
            e.printStackTrace();
            //do nothing
        }

        final Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable() {
            //run error handler every 2 seconds if executing
            @Override
            public void run() {
                if(sugiliteData.getInstructionQueueSize() > 0)
                    sugiliteData.errorHandler.checkError(sugiliteData.peekInstructionQueue());
                handler1.postDelayed(this, 2000);
            }
        }, 2000);

        final Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(sugiliteData.getInstructionQueueSize() > 0)
                    statusIconManager.refreshStatusIcon(null, null, true);
                handler1.postDelayed(this, 500);
            }
        }, 500);
    }


    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d( TAG, "inside onServiceCreated");

    }

    /**
     * run the runnable on UI thread
     * @param runnable
     */
    public void runOnUiThread(Runnable runnable) {
        Handler h = new Handler(getApplicationContext().getMainLooper());
        h.post(runnable);

    }



    protected HashSet<Map.Entry<String, String>> availableAlternatives;
    protected HashSet<SerializableNodeInfo> availableAlternativeNodes;
    protected HashSet<Map.Entry<String, String>> packageVocabs;

    protected HashSet<Map.Entry<String, String>> trackingPackageVocabs;

    Set<String> exceptedPackages = new HashSet<>();
    Set<String> trackingExcludedPackages = new HashSet<>();

    String previousClickText = "NULL", previousClickContentDescription = "NULL", previousClickChildText = "NULL", previousClickChildContentDescription = "NULL", previousClickPackageName = "NULL";

    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        //TODO problem: the status of "right after click" (try getParent()?)
        //TODO new rootNode method
        final AccessibilityNodeInfo sourceNode = event.getSource();
        AccessibilityNodeInfo rootNode = null;
        List<AccessibilityNodeInfo> preOrderTraverseSourceNode = null;
        List<AccessibilityNodeInfo> preOrderTraverseRootNode = null;
        List<AccessibilityNodeInfo> preOrderTraverseSibNode = null;


        //Type of accessibility events to handle in this function
        //return if the event is not among the accessibilityEventArrayToHandle
        if(!accessibilityEventSetToHandle.contains(event.getEventType())) {
            return;
        }

        //check for the trigger, see if an app launch trigger should be triggered
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            if(event.getSource() != null && event.getSource().getPackageName() != null && (!lastPackageName.contentEquals(event.getSource().getPackageName()))) {
                triggerHandler.checkForAppLaunchTrigger(event.getPackageName().toString());
                //lastPackageName used to avoid sync issue between threads
                lastPackageName = event.getSource().getPackageName().toString();
            }
        }

        //check communication status

        /* temporarily disable the communication controller for performance optimization
        if(sugiliteData.communicationController != null){
            if(!sugiliteData.communicationController.checkConnectionStatus())
                sugiliteData.communicationController.start();
        }
        */

        //add previous click information for building UI hierachy from vocabs
        if(BUILDING_VOCAB && (!trackingExcludedPackages.contains(event.getPackageName().toString())) && event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED && sourceNode != null){
            if(sourceNode.getText() != null)
                previousClickText = sourceNode.getText().toString();
            else
                previousClickText = "NULL";
            if(sourceNode.getContentDescription() != null)
                previousClickContentDescription = sourceNode.getContentDescription().toString();
            else
                previousClickContentDescription = "NULL";
            if(sourceNode.getPackageName() != null)
                previousClickPackageName = sourceNode.getPackageName().toString();
            else
                previousClickPackageName = "NULL";
            if(preOrderTraverseSourceNode == null)
                preOrderTraverseSourceNode = AutomatorUtil.preOrderTraverse(sourceNode);

            Set<String> childTexts = new HashSet<>();
            Set<String> childContentDescriptions = new HashSet<>();
            for(AccessibilityNodeInfo childNode : preOrderTraverseSourceNode){
                if(childNode.getText() != null)
                    childTexts.add(childNode.getText().toString());
                if(childNode.getContentDescription() != null)
                    childContentDescriptions.add(childNode.getContentDescription().toString());
            }
            // TODO: add sibling stuff here?
            if(childTexts.size() > 0)
                previousClickChildText = childTexts.toString();
            else
                previousClickChildText = "NULL";
            if(childContentDescriptions.size() > 0)
                previousClickContentDescription = childContentDescriptions.toString();
            else
                previousClickContentDescription = "NULL";
        }



        //packages within the excepted package will be totally excepted from the accessibility service tracking
        exceptedPackages.addAll(Arrays.asList(Const.ACCESSIBILITY_SERVICE_EXCEPTED_PACKAGE_NAMES));
        exceptedPackages.addAll(Arrays.asList(Const.INPUT_METHOD_PACKAGE_NAMES));
        trackingExcludedPackages.addAll(Arrays.asList(Const.ACCESSIBILITY_SERVICE_TRACKING_EXCLUDED_PACKAGE_NAMES));


        if (sugiliteData.getInstructionQueueSize() > 0 && !sharedPreferences.getBoolean("recording_in_process", true) && !exceptedPackages.contains(event.getPackageName()) && sugiliteData.errorHandler != null){
            //if the script running is in progress, invoke the error handler
            sugiliteData.errorHandler.checkError(event, sugiliteData.peekInstructionQueue(), Calendar.getInstance().getTimeInMillis());
        }

        /*
        if(verbalInstructionIconManager.isShowingIcon() && event.getPackageName() != null && (! exceptedPackages.contains(event.getPackageName()))) {
            //generate the ui snapshot and send to verbal instruction manager
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT ||
                    event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                //updateUISnapshotInVerbalInstructionManager();
            }
        }
        */



        if (sharedPreferences.getBoolean("recording_in_process", false)) {
            //if recording is in progress
            if(rootNode == null)
                rootNode = getRootInActiveWindow();
            if(preOrderTraverseSourceNode == null)
                preOrderTraverseSourceNode = AutomatorUtil.preOrderTraverse(sourceNode);
            if(preOrderTraverseRootNode == null)
                preOrderTraverseRootNode = AutomatorUtil.preOrderTraverse(rootNode);

            if(sourceNode != null && sourceNode.getClassName().toString().contains("EditText")){
                if(preOrderTraverseSibNode == null) {
                    preOrderTraverseSibNode = AutomatorUtil.preOrderTraverseSiblings(sourceNode);
                }
            }

            if(preOrderTraverseSibNode == null) {
                preOrderTraverseSibNode = AutomatorUtil.preOrderTraverseSiblings(sourceNode);
            }
            if(preOrderTraverseSibNode != null){
                preOrderTraverseSibNode.removeIf(n -> n.getText() == null);
            }




            final AccessibilityNodeInfo rootNodeForRecording = rootNode;
            final List<AccessibilityNodeInfo> preOrderTraverseSourceNodeForRecording = preOrderTraverseSourceNode;
            final List<AccessibilityNodeInfo> preOrderTracerseRootNodeForRecording = preOrderTraverseRootNode;
            final List<AccessibilityNodeInfo> preOrderTraverseSibNodeForRecording = preOrderTraverseSibNode;


            //add package name to the relevant package set
            if(sugiliteData.getScriptHead() != null && event.getPackageName() != null && (!exceptedPackages.contains(event.getPackageName().toString()))) {
                sugiliteData.getScriptHead().relevantPackages.add(event.getPackageName().toString());
            }
            //skip internal interactions and interactions on system ui
            availableAlternatives.addAll(getAlternativeLabels(sourceNode, rootNodeForRecording, preOrderTracerseRootNodeForRecording));
            availableAlternativeNodes.addAll(getAvailableAlternativeNodes(sourceNode, rootNodeForRecording, preOrderTracerseRootNodeForRecording));

            //refresh the elementsWithTextLabels list
            if(KEEP_ALL_TEXT_LABEL_LIST && event.getPackageName() != null && (!exceptedPackages.contains(event.getPackageName().toString()))){
                List<AccessibilityNodeInfo> nodes = getAllNodesWithText(rootNodeForRecording, preOrderTracerseRootNodeForRecording);
                boolean toRefresh = true;
                //hack used to avoid getting items in the duck popup
                if(nodes.size() > 10)
                    toRefresh = true;
                else {
                    for(AccessibilityNodeInfo node : nodes){
                        if(node.getText() != null && node.getText().toString().contains("Quit Sugilite")){
                            toRefresh = false;
                            break;
                        }
                    }
                    if(nodes.size() <= 0)
                        toRefresh = false;
                }
                if(toRefresh) {
                    sugiliteData.elementsWithTextLabels.clear();
                    sugiliteData.elementsWithTextLabels.addAll(nodes);
                }
                //System.out.println(event.getPackageName() + " " + sugiliteData.elementsWithTextLabels.size());
            }

            /*
            //EXPERIMENTAL: for recording overlay
            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED){
                //generate the uiSnapshot
                AccessibilityNodeInfo new_root = getRootInActiveWindow();
                if(new_root != null) {
                    final AccessibilityNodeInfo final_root = new_root;
                    UISnapshot uiSnapshot = new UISnapshot(final_root, true, sugiliteTextAnnotator);
                    System.out.println("ADDING OVERLAYS");
                    recordingOverlayManager.addAllOverlaysFromSnapshot(uiSnapshot);
                }
                else{
                    System.out.println("NULL ROOT");
                }
            }
            //EXPERIMENTAL: for recording overlay\
            */


            //if the event is to be recorded, process it
            if (accessibilityEventSetToSend.contains(event.getEventType()) &&
                    (!exceptedPackages.contains(event.getPackageName().toString())) &&
                    (!recordingOverlayManager.isShowingOverlay())) {

                //TODO: ignore events if the recording overlay is on

                //ignore automatically generated events
                long currentTime = Calendar.getInstance().getTimeInMillis();
                boolean toSkip = false;
                while(sugiliteData.NodeToIgnoreRecordingBoundsInScreenTimeStampQueue.size() > 0){
                    long timeStamp = sugiliteData.NodeToIgnoreRecordingBoundsInScreenTimeStampQueue.peek().getValue();
                    if(currentTime - timeStamp > 3000){
                        sugiliteData.NodeToIgnoreRecordingBoundsInScreenTimeStampQueue.remove();
                    }
                    else{
                        String boundsInScreen = sugiliteData.NodeToIgnoreRecordingBoundsInScreenTimeStampQueue.poll().getKey();
                        Rect rectForEvent = new Rect();
                        if(sourceNode != null){
                            sourceNode.getBoundsInScreen(rectForEvent);
                        }
                        if(boundsInScreen != null && boundsInScreen.equals(rectForEvent.flattenToString())){
                            toSkip = true;
                        }
                        break;
                    }
                }


                //generate the uiSnapshot
                AccessibilityNodeInfo new_root = sourceNode;
                while (new_root.getParent() != null){
                    new_root = new_root.getParent();
                }
                final AccessibilityNodeInfo final_root = new_root;


                if(sharedPreferences.getBoolean("recording_in_process", false)) {
                    //==== the thread of handling recording
                    Runnable handleRecording = new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Entering handle recording thread");
                            UISnapshot uiSnapshot = new UISnapshot(final_root, true, sugiliteTextAnnotator);

                            //temp hack for ViewGroup in Google Now Launcher
                            if (sourceNode != null && sourceNode.getClassName() != null && sourceNode.getPackageName() != null && sourceNode.getClassName().toString().contentEquals("android.view.ViewGroup") && homeScreenPackageNameSet.contains(sourceNode.getPackageName().toString())) {
                                /*do nothing (don't show popup for ViewGroup in home screen)*/
                            }

                            else {
                                File screenshot = null;
                                if (sharedPreferences.getBoolean("root_enabled", false)) {
                                    //1. take screenshot
                                    try {
                                    /*
                                    System.out.println("taking screen shot");
                                    screenshot = screenshotManager.take(false);
                                    */
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }


                                //2. send the event to recording pop up dialog
                                SugiliteAvailableFeaturePack featurePack = generateFeaturePack(event, sourceNode, rootNodeForRecording, screenshot, availableAlternativeNodes, preOrderTraverseSourceNodeForRecording, preOrderTracerseRootNodeForRecording, preOrderTraverseSibNodeForRecording);
                                LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());

                                if (featurePack.isEditable) {
                                    //3. handle text entry
                                    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                                        //TODO: add TextChangedEventHandlerHere
                                        textChangedEventHandler.handle(featurePack, availableAlternatives, layoutInflater, handler);
                                    }
                                } else {
                                    System.out.println("flush from service");
                                    //flush the text changed event handler
                                    textChangedEventHandler.flush();

                                    newDemonstrationHandler.handleEvent(featurePack, availableAlternatives, uiSnapshot);
                                    /*
                                    RecordingPopUpDialog recordingPopUpDialog = new RecordingPopUpDialog(sugiliteData, context, featurePack, sharedPreferences, layoutInflater, RecordingPopUpDialog.TRIGGERED_BY_NEW_EVENT, availableAlternatives);
                                    sugiliteData.recordingPopupDialogQueue.add(recordingPopUpDialog);

                                    if (!sugiliteData.recordingPopupDialogQueue.isEmpty() && sugiliteData.hasRecordingPopupActive == false) {
                                        sugiliteData.hasRecordingPopupActive = true;
                                        sugiliteData.recordingPopupDialogQueue.poll().show();
                                    }
                                    */
                                }
                            }


                            if (BUILDING_VOCAB) {
                                //add alternative nodes to the app vocab set
                                for (SerializableNodeInfo node : availableAlternativeNodes) {
                                    if (node.packageName != null && node.text != null)
                                        packageVocabs.add(new AbstractMap.SimpleEntry<String, String>(node.packageName, node.text));
                                    if (node.packageName != null && node.childText != null && node.childText.size() > 0) {
                                        for (String childText : node.childText)
                                            packageVocabs.add(new AbstractMap.SimpleEntry<String, String>(node.packageName, childText));
                                    }
                                }
                            }

                            if (accessibilityEventSetToSend.contains(event.getEventType()) && (!exceptedPackages.contains(event.getPackageName()))) {

                                if (BUILDING_VOCAB) {
                                    for (Map.Entry<String, String> entry : packageVocabs) {
                                        try {
                                            vocabularyDao.save(entry.getKey(), entry.getValue(), "meh", previousClickText, previousClickContentDescription, previousClickChildText, previousClickChildContentDescription, previousClickPackageName);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }

                                availableAlternatives.clear();
                                availableAlternativeNodes.clear();
                                if (BUILDING_VOCAB)
                                    packageVocabs.clear();
                            }

                        }
                    };
                    if (!toSkip) {
                        executor.execute(handleRecording);
                        //new Thread(handleRecording).run();
                    }
                }
                /*
                else if(sugiliteStudyHandler.isToRecordNextOperation()){
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            UISnapshot uiSnapshot = new UISnapshot(final_root, true, sugiliteTextAnnotator);
                            SugiliteAvailableFeaturePack featurePack = generateFeaturePack(event, sourceNode, rootNodeForRecording, null, availableAlternativeNodes, preOrderTraverseSourceNodeForRecording, preOrderTracerseRootNodeForRecording, preOrderTraverseSibNodeForRecording);
                            sugiliteStudyHandler.handleEvent(featurePack, uiSnapshot);
                        }
                    });
                }
                */

            }

        }

        if(sharedPreferences.getBoolean("broadcasting_enabled", false)) {
            if (accessibilityEventSetToTrack.contains(event.getEventType()) && (!trackingExcludedPackages.contains(event.getPackageName()))) {
                sugiliteData.handleBroadcastingEvent(event);
            }
        }

        ////// broadcast the accessibility event received, for any app that may want to listen

        if(BROADCASTING_ACCESSIBILITY_EVENT) { // if broadcasting is enabled
            try {
               if (accessibilityEventSetToTrack.contains(event.getEventType()) && (!trackingExcludedPackages.contains(event.getPackageName()))) {
                    SugiliteEventBroadcastingActivity.BroadcastingEvent broadcastingEvent = new SugiliteEventBroadcastingActivity.BroadcastingEvent(event);
                    Gson gson = new Gson();

                    // what is teh event ? find the properties.
                    String desc = broadcastingEvent.contentDescription;
                    String pkg = broadcastingEvent.packageName;
                    String event_type = broadcastingEvent.eventType;

                    // filter on what kinds of events should to be broadcasted ...
                    //if(desc.contentEquals("Home") && event_type.contentEquals("TYPE_VIEW_CLICKED") && pkg.contentEquals("com.android.systemui"))
                    if (true) {
                        String messageToSend = gson.toJson(broadcastingEvent);

                        // send info to middleware
//                        sugiliteData.communicationController.sendMessage( Const.RESPONSE,
//                                Const.ACCESSIBILITY_EVENT, messageToSend);

                        // broadcast info for receivers of other apps
                        Intent intent = new Intent();
                        intent.setAction("edu.cmu.hcii.sugilite.SUGILITE_ACC_EVENT");
                        intent.putExtra("accEvent", messageToSend);
                        sendBroadcast(intent);
                    }
                }
            }

            catch (Exception e) {
                //empty catch block
            }
        }

        if (sharedPreferences.getBoolean("tracking_in_process", false)) {
            if(rootNode == null)
                rootNode = getRootInActiveWindow();

            if(preOrderTraverseSourceNode == null)
                preOrderTraverseSourceNode = AutomatorUtil.preOrderTraverse(sourceNode);

            if(preOrderTraverseRootNode == null)
                preOrderTraverseRootNode = AutomatorUtil.preOrderTraverse(rootNode);

            if(preOrderTraverseSibNode == null) {
                preOrderTraverseSibNode = AutomatorUtil.preOrderTraverseSiblings(sourceNode);
            }
            if(preOrderTraverseSibNode != null){
                preOrderTraverseSibNode.removeIf(n -> n.getText() == null);
            }

            final AccessibilityNodeInfo rootNodeForTracking = rootNode;
            final List<AccessibilityNodeInfo> preOrderTraverseSourceNodeForTracking = preOrderTraverseSourceNode;
            final List<AccessibilityNodeInfo> preOrderTracerseRootNodeForTracking = preOrderTraverseRootNode;
            final List<AccessibilityNodeInfo> preOrderTraverseSibNodeForTracking = preOrderTraverseSibNode;


            Runnable handleTracking = new Runnable() {
                @Override
                public void run() {
                    //background tracking in progress
                    if (accessibilityEventSetToTrack.contains(event.getEventType()) && (!trackingExcludedPackages.contains(event.getPackageName().toString()))) {
                        sugilteTrackingHandler.handle(event, sourceNode, generateFeaturePack(event, sourceNode, rootNodeForTracking, null, null, preOrderTraverseSourceNodeForTracking, preOrderTracerseRootNodeForTracking, preOrderTraverseSibNodeForTracking));
                    }

                    //add all seen clickable nodes to package vocab DB
                    if(BUILDING_VOCAB) {
                        for (SerializableNodeInfo node : getAvailableAlternativeNodes(sourceNode, rootNodeForTracking, preOrderTracerseRootNodeForTracking)) {
                            if (node.packageName != null && node.text != null)
                                packageVocabs.add(new AbstractMap.SimpleEntry<>(node.packageName, node.text));
                            if (node.packageName != null && node.childText != null && node.childText.size() > 0) {
                                for (String childText : node.childText)
                                    packageVocabs.add(new AbstractMap.SimpleEntry<>(node.packageName, childText));
                            }
                        }
                        //only read/write DB at every click -> to optimize performance
                        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                            for (Map.Entry<String, String> entry : packageVocabs) {
                                try {
                                    vocabularyDao.save(entry.getKey(), entry.getValue(), "meh", previousClickText, previousClickContentDescription, previousClickChildText, previousClickChildContentDescription, previousClickPackageName);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            trackingPackageVocabs.clear();
                        }
                    }

                }
            };
            //new Thread(handleTracking).run();
            executor.execute(handleTracking);

        }


        SugiliteBlock currentBlock = sugiliteData.peekInstructionQueue();
        //refresh status icon
        if(currentBlock instanceof SugiliteOperationBlock) {
            if(rootNode == null)
                rootNode = getRootInActiveWindow();
            statusIconManager.refreshStatusIcon(rootNode, ((SugiliteOperationBlock) currentBlock).getElementMatchingFilter(), true);
        }
        else{
            statusIconManager.refreshStatusIcon(null, null, false);
        }


        //temporarily disable running automation from accessibility events

        /*
        if(sugiliteData.getInstructionQueueSize() > 0) {
            //System.out.println("attemping to run automation on the rootnode " + rootNode);
            //run automation
            if(rootNode == null)
                rootNode = getRootInActiveWindow();
            final AccessibilityNodeInfo finalRootNode = rootNode;
            if(automatorThread == null) {
                automatorThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        automator.handleLiveEvent(finalRootNode, getApplicationContext());
                        automatorThread = null;
                    }
                });
                automatorThread.start();
            }
        }
        */
    }




    @Override
    public void onInterrupt() {
        System.out.print("");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Toast.makeText(this, "Sugilite Accessibility Service Stopped", Toast.LENGTH_SHORT).show();
        if(statusIconManager != null)
            try {
                statusIconManager.removeStatusIcon();
            }
            catch (Exception e){
                //failed to remove status icon
                e.printStackTrace();
            }

    }

    public void updateUISnapshotInVerbalInstructionManager(){
        try {
            //for profiling purpose
            long startTime = System.currentTimeMillis();

            List<AccessibilityWindowInfo> windows = getWindows();
            verbalInstructionIconManager.rotateStatusIcon();
            if (windows.size() > 0) {
                Set<String> rootNodePackageNames = new HashSet<>();
                for (AccessibilityWindowInfo window : windows) {
                    if (window.getRoot() != null && window.getRoot().getPackageName() != null) {
                        rootNodePackageNames.add(window.getRoot().getPackageName().toString());
                    }
                }
                //if(!rootNodePackageNames.contains("edu.cmu.hcii.sugilite")) {
                if (true) {
                    try {
                        uiSnapshotGenerationExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                UISnapshot uiSnapshot = new UISnapshot(windows, true, sugiliteTextAnnotator);
                                if (uiSnapshot.getNodeAccessibilityNodeInfoMap().size() >= 5) {
                                    //filter out (mostly) empty ui snapshots
                                    verbalInstructionIconManager.setLatestUISnapshot(uiSnapshot);
                                    System.out.println("updated ui snapshot in verbal instruction manager");
                                    long stopTime = System.currentTimeMillis();
                                    Log.i(TAG, "Updated UI Snapshot! -- Takes " + String.valueOf(stopTime - startTime) + "ms");
                                }
                            }
                        });
                    }
                    catch (RejectedExecutionException e){
                        //do nothing
                    }
                }
            }

            //for profiling purpose

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void checkIfAutomationCanBePerformed(){
        //TODO: fill in check for whether automation can be performed
        if(sugiliteData.getInstructionQueueSize() > 0) {
            //System.out.println("attemping to run automation on the rootnode " + rootNode);
            //run automation
            List<AccessibilityWindowInfo> windows = getWindows();
            UISnapshot uiSnapshot = new UISnapshot(windows, true, sugiliteTextAnnotator);
            List<AccessibilityNodeInfo> allNodes = AutomatorUtil.getAllNodesFromWindows(windows);
            if(automatorThread == null) {
                automatorThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        automator.handleLiveEvent(uiSnapshot, getApplicationContext(), allNodes);
                        automatorThread = null;
                    }
                });
                automatorThread.start();
            }
        }
    }


    protected SugiliteAvailableFeaturePack generateFeaturePack(AccessibilityEvent event, AccessibilityNodeInfo sourceNode, AccessibilityNodeInfo rootNode, File screenshot, HashSet<SerializableNodeInfo> availableAlternativeNodes, List<AccessibilityNodeInfo> preOrderTraverseSourceNode, List<AccessibilityNodeInfo> preOderTraverseRootNode, List<AccessibilityNodeInfo> preOrderTraverseSibNode){
        SugiliteAvailableFeaturePack featurePack = new SugiliteAvailableFeaturePack();
        Rect boundsInParents = new Rect();
        Rect boundsInScreen = new Rect();
        AccessibilityNodeInfo parentNode = null;
        if(sourceNode != null) {
            sourceNode.getBoundsInParent(boundsInParents);
            sourceNode.getBoundsInScreen(boundsInScreen);
            parentNode = sourceNode.getParent();
        }

        //NOTE: NOT ONLY COUNTING THE IMMEDIATE CHILDREN NOW
        ArrayList<AccessibilityNodeInfo> childrenNodes = null;
        ArrayList<AccessibilityNodeInfo> siblingNodes = null;
        if(sourceNode != null && preOrderTraverseSourceNode != null) {
            childrenNodes = new ArrayList<>(preOrderTraverseSourceNode);
        }
        else
            childrenNodes = new ArrayList<>();
        if(sourceNode != null && preOrderTraverseSibNode != null)
            siblingNodes = new ArrayList<>(preOrderTraverseSibNode);
        else
            siblingNodes = new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> allNodes = null;
        if(rootNode != null && preOderTraverseRootNode != null)
            allNodes = new ArrayList<>(preOderTraverseRootNode);
        else
            allNodes = new ArrayList<>();
        //TODO:AccessibilityNodeInfo is not serializable

        if(sourceNode == null || sourceNode.getPackageName() == null){
            featurePack.packageName = "NULL";
        }
        else
            featurePack.packageName = sourceNode.getPackageName().toString();

        if(sourceNode == null || sourceNode.getClassName() == null){
            featurePack.className = "NULL";
        }
        else
            featurePack.className = sourceNode.getClassName().toString();

        if(sourceNode == null || sourceNode.getText() == null){
            featurePack.text = "NULL";
        }
        else
            featurePack.text = sourceNode.getText().toString();

        if(sourceNode == null || sourceNode.getContentDescription() == null){
            featurePack.contentDescription = "NULL";
        }
        else
            featurePack.contentDescription = sourceNode.getContentDescription().toString();

        if(sourceNode == null || sourceNode.getViewIdResourceName() == null){
            featurePack.viewId = "NULL";
        }
        else
            featurePack.viewId = sourceNode.getViewIdResourceName();

        featurePack.boundsInParent = boundsInParents.flattenToString();
        featurePack.boundsInScreen = boundsInScreen.flattenToString();
        featurePack.time = Calendar.getInstance().getTimeInMillis();
        featurePack.eventType = event.getEventType();
        featurePack.parentNode = new SerializableNodeInfo(parentNode);
        featurePack.childNodes = new AccessibilityNodeInfoList(childrenNodes).getSerializableList();
        // TODO: if it's slow, then use serializednode instead
        featurePack.siblingNodes = new AccessibilityNodeInfoList(siblingNodes).getSerializableList();
        featurePack.allNodes = new AccessibilityNodeInfoList(allNodes).getSerializableList();
        if(sourceNode != null)
            featurePack.isEditable = sourceNode.isEditable();
        else
            featurePack.isEditable = false;
        featurePack.screenshot = screenshot;
        if(availableAlternativeNodes != null)
            featurePack.alternativeNodes = new HashSet<>(availableAlternativeNodes);
        else
            featurePack.alternativeNodes = new HashSet<>();

        if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED){
            if(event.getBeforeText() == null) {
                featurePack.beforeText = "NULL";
            }
            else{
                featurePack.beforeText = event.getBeforeText().toString();
            }
        }

        featurePack.childTexts = new ArrayList<>();
        if(featurePack.childNodes != null){
            for(SerializableNodeInfo node : featurePack.childNodes){
                featurePack.childTexts.add(node.text);
            }
        }

        return featurePack;


    }

    protected List<AccessibilityNodeInfo> getAllNodesWithText(AccessibilityNodeInfo rootNode, List<AccessibilityNodeInfo> preOderTraverseRootNode) {
        List<AccessibilityNodeInfo> retList = new ArrayList<>();
        if (preOderTraverseRootNode == null)
            return retList;
        for (AccessibilityNodeInfo node : preOderTraverseRootNode) {
            if(node.getText() != null)
                retList.add(node);
        }
        return retList;
    }

    protected HashSet<Map.Entry<String, String>> getAlternativeLabels (AccessibilityNodeInfo sourceNode, AccessibilityNodeInfo rootNode, List<AccessibilityNodeInfo> preOderTraverseRootNode){
        HashSet<Map.Entry<String, String>> retMap = new HashSet<>();
        if(preOderTraverseRootNode == null)
            return retMap;
        for(AccessibilityNodeInfo node : preOderTraverseRootNode){
            if(exceptedPackages.contains(node.getPackageName().toString()))
                continue;
            if(!node.isClickable())
                continue;
            if(!(sourceNode == null || (sourceNode.getClassName() == null && node.getClassName() == null) || (sourceNode.getClassName() != null && node.getClassName() != null && sourceNode.getClassName().toString().contentEquals(node.getClassName()))))
                continue;
            if(node.getText() != null)
                retMap.add(new AbstractMap.SimpleEntry<>("Text", node.getText().toString()));
            if(node.getContentDescription() != null)
                retMap.add(new AbstractMap.SimpleEntry<>("ContentDescription", node.getContentDescription().toString()));
            List<AccessibilityNodeInfo> childNodes = AutomatorUtil.preOrderTraverse(node);
            if(childNodes == null)
                continue;
            for(AccessibilityNodeInfo childNode : childNodes){
                if(childNode == null)
                    continue;
                if(childNode.getText() != null)
                    retMap.add(new AbstractMap.SimpleEntry<>("Child Text", childNode.getText().toString()));
                if(childNode.getContentDescription() != null)
                    retMap.add(new AbstractMap.SimpleEntry<>("Child ContentDescription", childNode.getContentDescription().toString()));
            }
        }
        return retMap;
    }

    public StatusIconManager getDuckIconManager() {
        return statusIconManager;
    }

    public VerbalInstructionIconManager getVerbalInstructionIconManager() {
        return verbalInstructionIconManager;
    }

    public SugiliteStudyHandler getSugiliteStudyHandler() {
        return sugiliteStudyHandler;
    }

    /**
     * get alternative nodes: anything that is clickable, of the same class type as the source node
     * and not in the excepted packages
     *
     * @param sourceNode
     * @param rootNode
     * @return
     */
    protected HashSet<SerializableNodeInfo> getAvailableAlternativeNodes (AccessibilityNodeInfo sourceNode, AccessibilityNodeInfo rootNode, List<AccessibilityNodeInfo> preOderTraverseRootNode){
        HashSet<SerializableNodeInfo> retSet = new HashSet<>();
        if(preOderTraverseRootNode == null)
            return retSet;
        for(AccessibilityNodeInfo node : preOderTraverseRootNode){
            if(exceptedPackages.contains(node.getPackageName().toString()))
                continue;
            if(sourceNode != null &&
                    node.getClassName() != null &&
                    sourceNode.getClassName() != null &&
                    (!sourceNode.getClassName().toString().equals(node.getClassName().toString())))
                continue;
            if(!node.isClickable())
                continue;
            SerializableNodeInfo nodeToAdd = new SerializableNodeInfo(node);
            retSet.add(nodeToAdd);
        }
        return retSet;
    }
}



