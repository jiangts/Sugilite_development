package edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.LightingColorFilter;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.hcii.sugilite.model.Node;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteAccessibilityService;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.operation.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.operation.SugiliteOperation;
import edu.cmu.hcii.sugilite.model.operation.SugiliteUnaryOperation;
import edu.cmu.hcii.sugilite.ontology.OntologyQuery;
import edu.cmu.hcii.sugilite.ontology.OntologyQueryUtils;
import edu.cmu.hcii.sugilite.ontology.SerializableOntologyQuery;
import edu.cmu.hcii.sugilite.ontology.SerializableUISnapshot;
import edu.cmu.hcii.sugilite.ontology.SugiliteEntity;
import edu.cmu.hcii.sugilite.ontology.SugiliteRelation;
import edu.cmu.hcii.sugilite.ontology.UISnapshot;
import edu.cmu.hcii.sugilite.ontology.description.OntologyDescriptionGenerator;
import edu.cmu.hcii.sugilite.recording.newrecording.SugiliteBlockBuildingHelper;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogManager;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogSimpleState;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogUtteranceFilter;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.VerbalInstructionRecordingManager;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.SugiliteVerbalInstructionHTTPQueryInterface;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.SugiliteVerbalInstructionHTTPQueryManager;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.VerbalInstructionServerQuery;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.VerbalInstructionServerResults;

import static edu.cmu.hcii.sugilite.Const.MUL_ZEROS;
import static edu.cmu.hcii.sugilite.Const.RECORDING_DARK_GRAY_COLOR;
import static edu.cmu.hcii.sugilite.Const.RECORDING_OFF_BUTTON_COLOR;
import static edu.cmu.hcii.sugilite.Const.boldify;

/**
 * @author toby
 * @date 2/11/18
 * @time 11:55 PM
 */
public class RecordingAmbiguousPopupDialog extends SugiliteDialogManager implements SugiliteVerbalInstructionHTTPQueryInterface {
    private List<Pair<SerializableOntologyQuery, Double>> queryScoreList;
    private SugiliteAvailableFeaturePack featurePack;
    private EditText verbalInstructionEditText;
    private SugiliteVerbalInstructionHTTPQueryManager sugiliteVerbalInstructionHTTPQueryManager;
    private Dialog dialog;
    private View dialogView;
    private AlertDialog progressDialog;
    private Gson gson;
    private SerializableUISnapshot serializableUISnapshot;
    private UISnapshot uiSnapshot;
    private SugiliteEntity<Node> actualClickedNode;
    private SugiliteBlockBuildingHelper blockBuildingHelper;
    private Runnable clickRunnable;
    private LayoutInflater layoutInflater;
    private VerbalInstructionRecordingManager verbalInstructionRecordingManager;
    private SugiliteData sugiliteData;
    private SharedPreferences sharedPreferences;
    private TextView textPrompt;
    private OntologyDescriptionGenerator descriptionGenerator;
    private ImageButton mySpeakButton;

    private int errorCount = 0;
    public static boolean CHECK_FOR_GROUNDING_MATCH = false;

    //states
    private SugiliteDialogSimpleState askingForVerbalInstructionState = new SugiliteDialogSimpleState("ASKING_FOR_VERBAL_INSTRUCTION", this);
    private SugiliteDialogSimpleState askingForInstructionConfirmationState = new SugiliteDialogSimpleState("ASKING_FOR_INSTRUCTION_CONFIRMATION", this);
    private SugiliteDialogSimpleState emptyResultState = new SugiliteDialogSimpleState("EMPTY_RESULT_STATE", this);
    private SugiliteDialogSimpleState resultWontMatchState = new SugiliteDialogSimpleState("RESULT_WONT_MATCH_STATE", this);


    public RecordingAmbiguousPopupDialog(Context context, List<Pair<SerializableOntologyQuery, Double>> queryScoreList, SugiliteAvailableFeaturePack featurePack, SugiliteBlockBuildingHelper blockBuildingHelper, LayoutInflater layoutInflater, Runnable clickRunnable, UISnapshot uiSnapshot, SugiliteEntity<Node> actualClickedNode, SugiliteData sugiliteData, SharedPreferences sharedPreferences, TextToSpeech tts, int errorCount) {
        super(context, tts);
        this.queryScoreList = queryScoreList;
        this.featurePack = featurePack;
        this.sugiliteVerbalInstructionHTTPQueryManager = new SugiliteVerbalInstructionHTTPQueryManager(this, sharedPreferences);
        this.descriptionGenerator = new OntologyDescriptionGenerator(context);

        //TODO: need to operate on a copy of ui snapshot
        this.uiSnapshot = uiSnapshot;
        this.serializableUISnapshot = new SerializableUISnapshot(uiSnapshot);
        this.actualClickedNode = actualClickedNode;
        this.blockBuildingHelper = blockBuildingHelper;
        this.clickRunnable = clickRunnable;
        this.layoutInflater = layoutInflater;
        this.sugiliteData = sugiliteData;
        this.sharedPreferences = sharedPreferences;
        this.gson = new Gson();
        this.verbalInstructionRecordingManager = new VerbalInstructionRecordingManager(context, sugiliteData, sharedPreferences);
        this.errorCount = errorCount;

        //build the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        //builder.setTitle("Select from disambiguation results");
        dialogView = layoutInflater.inflate(R.layout.dialog_ambiguous_popup_spoken, null);

        //set the list view for query parse candidates
        ListView mainListView = (ListView) dialogView.findViewById(R.id.listview_query_candidates);
        verbalInstructionEditText = (EditText) dialogView.findViewById(R.id.edittext_instruction_content);
        textPrompt = (TextView) dialogView.findViewById(R.id.text_prompt);

        //Map<TextView, SugiliteOperationBlock> textViews = new HashMap<>();

        queryScoreList = queryScoreList.subList(0, 1);
        String[] stringArray = new String[queryScoreList.size()];
        SugiliteOperationBlock[] sugiliteOperationBlockArray = new SugiliteOperationBlock[queryScoreList.size()];

        int i = 0;
        for (Pair<SerializableOntologyQuery, Double> entry : queryScoreList) {
            SugiliteOperationBlock block = blockBuildingHelper.getOperationBlockFromQuery(entry.first, SugiliteOperation.CLICK, featurePack);
            sugiliteOperationBlockArray[i++] = block;
        }

        Map<SugiliteOperationBlock, String> descriptions = blockBuildingHelper.getDescriptionsInDifferences(sugiliteOperationBlockArray);

        i = 0;
        for (SugiliteOperationBlock block : sugiliteOperationBlockArray) {
            stringArray[i++] = descriptions.get(block);
        }


        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, stringArray) {
            //override the arrayadapter to show HTML-styled textviews in the listview
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row;
                if (null == convertView) {
                    row = layoutInflater.inflate(android.R.layout.simple_list_item_1, null);
                } else {
                    row = convertView;
                }
                TextView tv = (TextView) row.findViewById(android.R.id.text1);
                tv.setText(Html.fromHtml(getItem(position)));
                //textViews.put(tv, sugiliteOperationBlockArray[position]);
                return row;
            }

        };
        mainListView.setAdapter(adapter);
        //finished setting up the parse result candidate list


        //initiate the speak button
        mySpeakButton  = (ImageButton) dialogView.findViewById(R.id.button_verbal_instruction_talk);
        mySpeakButton.getBackground().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_OFF_BUTTON_COLOR));
        mySpeakButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // speak button
                if (isListening() || tts.isSpeaking()) {
                    stopASRandTTS();
                } else {
                    initDialogManager();
                }
            }
        });
        mySpeakButton.setImageDrawable(notListeningDrawable);
        mySpeakButton.getDrawable().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_DARK_GRAY_COLOR));
        setSpeakButton(mySpeakButton);

        builder.setView(dialogView);

        //set the buttons
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sendInstructionButtonOnClick();
            }
        }).setNegativeButton("Skip", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                skipButtonOnClick();
            }
        }).setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });


        //on item click for query candidates
        mainListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //showConfirmation(sugiliteOperationBlockArray[position], featurePack, queryScoreList);
                if (sharedPreferences.getBoolean("recording_in_process", false)) {
                    try {
                        blockBuildingHelper.saveBlock(sugiliteOperationBlockArray[position], featurePack);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                clickRunnable.run();
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                stopASRandTTS();
            }
        });
    }



    public void show() {
        if(dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        dialog.show();

        //initiate the dialog manager when the dialog is shown
        initDialogManager();
        refreshSpeakButtonStyle(mySpeakButton);
    }

    private void showProgressDialog() {
        progressDialog = new AlertDialog.Builder(context).setMessage("Processing the query ...").create();
        if(progressDialog.getWindow() != null) {
            progressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
    }

    private void skipButtonOnClick(){
        clickRunnable.run();
        dialog.cancel();
    }

    private void sendInstructionButtonOnClick() {
        //send the instruction out to the server for semantic parsing
        if (verbalInstructionEditText != null) {
            String userInput = verbalInstructionEditText.getText().toString();
            String className = null;
            if(actualClickedNode != null && actualClickedNode.getEntityValue() != null){
                className = actualClickedNode.getEntityValue().getClassName();
            }

            //send out the ASR result
            VerbalInstructionServerQuery query = new VerbalInstructionServerQuery(userInput, serializableUISnapshot.triplesToStringWithFilter(SugiliteRelation.HAS_CHILD, SugiliteRelation.HAS_PARENT, SugiliteRelation.HAS_CONTENT_DESCRIPTION), className);
            //send the query
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        sugiliteVerbalInstructionHTTPQueryManager.sendQueryRequest(query);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();

            //show loading popup
            dialog.dismiss();
            showProgressDialog();
        }
    }

    /**
     * callback for the HTTP query when the result is available
     */
    @Override
    public void resultReceived(int responseCode, String result) {
        final int MAX_QUERY_CANDIDATE_NUMBER = 7;

        //dismiss the dialog
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        //raw response
        System.out.print(responseCode + ": " + result);

        //de-serialize to VerbalInstructionResults
        VerbalInstructionServerResults results = gson.fromJson(result, VerbalInstructionServerResults.class);

        if (results.getQueries() == null || results.getQueries().isEmpty()) {
            //error in parsing the server reply
            Toast.makeText(context, String.valueOf(responseCode) + ": Can't parse the verbal instruction", Toast.LENGTH_SHORT).show();
            dialog.show();
            setCurrentState(emptyResultState);
            initPrompt();
            return;
        }

        //print for debug purpose
        for (VerbalInstructionServerResults.VerbalInstructionResult verbalInstructionResult : results.getQueries()) {
            System.out.println(gson.toJson(verbalInstructionResult));
        }

        //find matches
        List<Pair<OntologyQuery, List<Node>>> matchingQueriesMatchedNodesList = new ArrayList<>();

        for (VerbalInstructionServerResults.VerbalInstructionResult verbalInstructionResult : results.getQueries()) {
            if((verbalInstructionResult.getGrounding() == null || verbalInstructionResult.getGrounding().isEmpty()) && CHECK_FOR_GROUNDING_MATCH){
                //empty grounding
                continue;
            }

            Set<String> groundings = new HashSet<>(verbalInstructionResult.getGrounding());
            if((!groundings.contains("@" + actualClickedNode.getEntityId().toString())) && CHECK_FOR_GROUNDING_MATCH){
                //grouding doesn't contain the clicked item
                continue;
            }


            boolean matched = false;
            List<Node> filteredNodes = new ArrayList<>();
            //Map<Node, Integer> filteredNodeNodeIdMap = new HashMap<>();

            //construct the query, run the query, and compare the result against the actually clicked on node

            String queryFormula = verbalInstructionResult.getFormula();
            OntologyQuery query = OntologyQueryUtils.getQueryWithClassAndPackageConstraints(OntologyQuery.deserialize(queryFormula), actualClickedNode.getEntityValue(), false, true, true);

            //TODO: fix the bug in query.executeOn -- it should not change the query

            try {
                OntologyQuery queryClone = OntologyQuery.deserialize(query.toString());
                Set<SugiliteEntity> queryResults =  queryClone.executeOn(uiSnapshot);
                for(SugiliteEntity entity : queryResults){
                    if(entity.getType().equals(Node.class)){
                        Node node = (Node) entity.getEntityValue();
                        if (node.getClickable()) {
                            filteredNodes.add(node);
                            //filteredNodeNodeIdMap.put(node, entity.getEntityId());
                        }
                        if (OntologyQueryUtils.isSameNode(actualClickedNode.getEntityValue(), node)) {
                            matched = true;
                        }


                    }
                }

                if(!CHECK_FOR_GROUNDING_MATCH){
                    matched = true;
                }

            }
            catch (Exception e){
                Gson gson = new Gson();
                e.printStackTrace();
                System.out.println("ERROR QUERY: " + queryFormula);
                System.out.println("ERROR QUERY JSON" + gson.toJson(query));
            }


            if (filteredNodes.size() > 0 && matched) {
                //matched, add the result to the list
                matchingQueriesMatchedNodesList.add(Pair.create(query, filteredNodes));
            }

            if(matchingQueriesMatchedNodesList.size() > MAX_QUERY_CANDIDATE_NUMBER){
                break;
            }
        }

        if(false) {
            //don't sort the results -- keep the original order from the parser
            matchingQueriesMatchedNodesList.sort(new Comparator<Pair<OntologyQuery, List<Node>>>() {
                @Override
                public int compare(Pair<OntologyQuery, List<Node>> o1, Pair<OntologyQuery, List<Node>> o2) {
                    return o1.second.size() - o2.second.size();
                /*
                if(o1.getValue().size() != o2.getValue().size()){
                    return o1.getValue().size() - o2.getValue().size();
                }
                else{
                    return o1.getKey().toString().length() - o2.getKey().toString().length();
                }
                */
                }
            });
        }



        if (matchingQueriesMatchedNodesList.size() == 1) {
            OntologyQuery query = matchingQueriesMatchedNodesList.get(0).first;
            Toast.makeText(context, query.toString(), Toast.LENGTH_SHORT).show();

            //check if this has filteredNodes.size() = 1 -- whether need to show the followup question dialog
            if(matchingQueriesMatchedNodesList.get(0).second.size() > 1) {
                //prompt for further generalization
                FollowUpQuestionDialog followUpQuestionDialog = new FollowUpQuestionDialog(context, tts, query, uiSnapshot, actualClickedNode, matchingQueriesMatchedNodesList.get(0).second, featurePack, queryScoreList, blockBuildingHelper, layoutInflater, clickRunnable, sugiliteData, sharedPreferences, 0);
                followUpQuestionDialog.setNumberOfMatchedNodes(matchingQueriesMatchedNodesList.get(0).second.size());
                followUpQuestionDialog.show();
            } else {
                //save the block and show a confirmation dialog for the block
                System.out.println("Result Query: " + query.toString());

                //construct a block from the query formula
                SerializableOntologyQuery serializableOntologyQuery = new SerializableOntologyQuery(query);
                SugiliteOperationBlock block = blockBuildingHelper.getOperationBlockFromQuery(serializableOntologyQuery, SugiliteOperation.CLICK, featurePack);

                //construct a confirmation dialog from the block
                showConfirmationDialog(block, featurePack, queryScoreList, clickRunnable);
            }
            dialog.dismiss();
        }

        else if(matchingQueriesMatchedNodesList.size() > 1){
            ChooseParsingDialog chooseParsingDialog = new ChooseParsingDialog(context, matchingQueriesMatchedNodesList.subList(0, Integer.min(matchingQueriesMatchedNodesList.size(), MAX_QUERY_CANDIDATE_NUMBER)), blockBuildingHelper, layoutInflater, clickRunnable, uiSnapshot, actualClickedNode, sugiliteData, sharedPreferences, tts, featurePack, queryScoreList);
            chooseParsingDialog.show();
            dialog.dismiss();
        }

        else {
            //empty result, show the dialog and switch to empty result state
            dialog.show();
            setCurrentState(resultWontMatchState);
            initPrompt();

            String descriptionForTopQuery = null;
            try {
                OntologyQuery topQuery = OntologyQuery.deserialize(results.getQueries().get(0).getFormula());
                SugiliteOperation operation = new SugiliteUnaryOperation(SugiliteOperation.CLICK);
                topQuery = OntologyQueryUtils.getQueryWithClassAndPackageConstraints(topQuery, actualClickedNode.getEntityValue(), false, true, true);
                descriptionForTopQuery = descriptionGenerator.getDescriptionForOperation(operation, new SerializableOntologyQuery(topQuery));
                if(descriptionForTopQuery != null){
                    textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_result_wont_match)) + "<br><br> Intepretation for your description: " + descriptionForTopQuery));

                }
            }
            catch (Exception e){
                e.printStackTrace();
            }


        }
    }

    private void showConfirmationDialog(SugiliteOperationBlock block, SugiliteAvailableFeaturePack featurePack, List<Pair<SerializableOntologyQuery, Double>> queryScoreList, Runnable clickRunnable) {
        SugiliteRecordingConfirmationDialog sugiliteRecordingConfirmationDialog = new SugiliteRecordingConfirmationDialog(context, block, featurePack, queryScoreList, clickRunnable, blockBuildingHelper, layoutInflater, uiSnapshot, actualClickedNode, sugiliteData, sharedPreferences, tts);
        sugiliteRecordingConfirmationDialog.show();
    }

    /**
     * initiate the dialog manager
     */
    @Override
    public void initDialogManager() {
        //set the prompt
        emptyResultState.setPrompt(context.getString(R.string.disambiguation_error));
        resultWontMatchState.setPrompt(context.getString(R.string.disambiguation_result_wont_match));
        askingForVerbalInstructionState.setPrompt(context.getString(R.string.disambiguation_prompt));

        //set on switched away runnable - the verbal instruction state should set the value for the text box
        askingForVerbalInstructionState.setOnSwitchedAwayRunnable(new Runnable() {
            @Override
            public void run() {
                if (askingForVerbalInstructionState.getASRResult() != null && (!askingForVerbalInstructionState.getASRResult().isEmpty())) {
                    verbalInstructionEditText.setText(askingForVerbalInstructionState.getASRResult().get(0));
                }
            }
        });
        emptyResultState.setOnSwitchedAwayRunnable(new Runnable() {
            @Override
            public void run() {
                if (emptyResultState.getASRResult() != null && (!emptyResultState.getASRResult().isEmpty())) {
                    verbalInstructionEditText.setText(emptyResultState.getASRResult().get(0));
                }
            }
        });

        resultWontMatchState.setOnSwitchedAwayRunnable(new Runnable() {
            @Override
            public void run() {
                if (resultWontMatchState.getASRResult() != null && (!resultWontMatchState.getASRResult().isEmpty())) {
                    verbalInstructionEditText.setText(resultWontMatchState.getASRResult().get(0));
                }
            }
        });

        askingForVerbalInstructionState.setOnInitiatedRunnable(new Runnable() {
            @Override
            public void run() {
                //clear the edittext
                verbalInstructionEditText.setText("");
                textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_prompt))));
            }
        });

        //set on initiate runnable - the instruction confirmation state should use the content in the text box as the prompt
        askingForInstructionConfirmationState.setOnInitiatedRunnable(new Runnable() {
            @Override
            public void run() {
                //askingForInstructionConfirmationState.setPrompt(context.getString(R.string.disambiguation_confirm, verbalInstructionEditText.getText()));
                askingForInstructionConfirmationState.setPrompt(context.getString(R.string.disambiguation_confirm));
                textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_confirm))));
            }
        });

        resultWontMatchState.setOnInitiatedRunnable(new Runnable() {
            @Override
            public void run() {
                //clear the edittext
                verbalInstructionEditText.setText("");

                //increment for error count
                errorCount ++;

                if(errorCount > 1){
                    //set prompt to the error one
                    resultWontMatchState.setPrompt(context.getString(R.string.disambiguation_result_wont_match_repeated));
                    textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_result_wont_match_repeated))));
                }

                else{
                    resultWontMatchState.setPrompt(context.getString(R.string.disambiguation_result_wont_match));
                    textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_result_wont_match))));
                }
            }
        });

        emptyResultState.setOnInitiatedRunnable(new Runnable() {
            @Override
            public void run() {
                //clear the edittext
                verbalInstructionEditText.setText("");

                //increment for error count
                errorCount ++;

                if(errorCount > 1){
                    //set prompt to the error one
                    emptyResultState.setPrompt(context.getString(R.string.disambiguation_error_repeated));
                    textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_error_repeated))));
                }

                else {
                    emptyResultState.setPrompt(context.getString(R.string.disambiguation_error));
                    textPrompt.setText(Html.fromHtml(boldify(context.getString(R.string.disambiguation_error))));
                }
            }
        });

        //link the states
        askingForVerbalInstructionState.setNoASRResultState(askingForVerbalInstructionState);
        askingForVerbalInstructionState.setUnmatchedState(askingForVerbalInstructionState);
        askingForVerbalInstructionState.addNextStateUtteranceFilter(askingForInstructionConfirmationState, SugiliteDialogUtteranceFilter.getConstantFilter(true));

        emptyResultState.setNoASRResultState(askingForVerbalInstructionState);
        emptyResultState.setUnmatchedState(askingForVerbalInstructionState);
        emptyResultState.addNextStateUtteranceFilter(askingForInstructionConfirmationState, SugiliteDialogUtteranceFilter.getConstantFilter(true));

        resultWontMatchState.setNoASRResultState(askingForVerbalInstructionState);
        resultWontMatchState.setUnmatchedState(askingForVerbalInstructionState);
        resultWontMatchState.addNextStateUtteranceFilter(askingForInstructionConfirmationState, SugiliteDialogUtteranceFilter.getConstantFilter(true));

        askingForInstructionConfirmationState.setNoASRResultState(askingForInstructionConfirmationState);
        askingForInstructionConfirmationState.setUnmatchedState(askingForInstructionConfirmationState);
        askingForInstructionConfirmationState.addNextStateUtteranceFilter(askingForVerbalInstructionState, SugiliteDialogUtteranceFilter.getSimpleContainingFilter("no", "nah"));

        //set exit runnables
        askingForVerbalInstructionState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("skip"), new Runnable() {
            @Override
            public void run() {
                skipButtonOnClick();
            }
        });
        askingForVerbalInstructionState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("cancel"), new Runnable() {
            @Override
            public void run() {
                dialog.cancel();
            }
        });
        askingForInstructionConfirmationState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("yes", "yeah"), new Runnable() {
            @Override
            public void run() {
                sendInstructionButtonOnClick();
            }
        });


        //set current sate
        setCurrentState(askingForVerbalInstructionState);
        initPrompt();
    }

    @Override
    public void runOnMainThread(Runnable r) {
        try {
            if (context instanceof SugiliteAccessibilityService) {
                ((SugiliteAccessibilityService) context).runOnUiThread(r);
            } else {
                throw new Exception("no access to ui thread");
            }
        } catch (Exception e) {
            //do nothing
            e.printStackTrace();
        }
    }
}
