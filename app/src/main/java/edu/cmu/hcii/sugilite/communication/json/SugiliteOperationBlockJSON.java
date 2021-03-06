package edu.cmu.hcii.sugilite.communication.json;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.model.block.util.SerializableNodeInfo;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.operation.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.operation.SugiliteOperation;
import edu.cmu.hcii.sugilite.model.operation.SugiliteReadoutOperation;
import edu.cmu.hcii.sugilite.model.operation.SugiliteSetTextOperation;
import edu.cmu.hcii.sugilite.model.operation.SugiliteUnaryOperation;
import edu.cmu.hcii.sugilite.recording.ReadableDescriptionGenerator;

/**
 * Created by toby on 7/14/16.
 */
public class SugiliteOperationBlockJSON {
    public SugiliteOperationBlockJSON(SugiliteOperationBlock block){
        //TODO: support alternative list
        switch (block.getOperation().getOperationType()){
            case SugiliteOperation.CLICK:
                actionType = "CLICK";
                break;
            case SugiliteOperation.LONG_CLICK:
                actionType = "LONG_CLICK";
                break;
            case SugiliteOperation.SET_TEXT:
                actionType = "SET_TEXT";
                if(block.getOperation() instanceof SugiliteSetTextOperation)
                    actionParameter = ((SugiliteSetTextOperation)block.getOperation()).getText();
                break;
            case SugiliteOperation.READ_OUT:
                actionType = "READ_OUT";
                actionParameter = ((SugiliteReadoutOperation)block.getOperation()).getPropertyToReadout();
                break;
            case SugiliteOperation.LOAD_AS_VARIABLE:
                actionType = "LOAD_AS_VARIABLE";
                break;
            case SugiliteOperation.SPECIAL_GO_HOME:
                actionType = "SPECIAL_GO_HOME";
                break;
            default:
                actionType = "UNDEFINED";
        }
        filter = new SugiliteFilterJSON(block.getElementMatchingFilter());
        if(block.getNextBlock() != null && block.getNextBlock() instanceof SugiliteOperationBlock)
            nextBlock = new SugiliteOperationBlockJSON((SugiliteOperationBlock)block.getNextBlock());
        createdTime = block.getCreatedTime();

        if(block.getFeaturePack() != null && Const.KEEP_ALL_NODES_IN_THE_FEATURE_PACK){
            SugiliteAvailableFeaturePack featurePack = block.getFeaturePack();
            List<SerializableNodeInfo> childNodes = featurePack.childNodes;
            if(childNodes != null && childNodes.size() > 0) {
                childTexts = new ArrayList<>();
                for (SerializableNodeInfo node : childNodes) {
                    childTexts.add(node.text);
                }
            }
        }

    }

    public SugiliteOperationBlock toSugiliteOperationBlock(Context context){
        ReadableDescriptionGenerator generator =  new ReadableDescriptionGenerator(context);
        SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
        SugiliteOperation operation;
        if(actionType.equals("SET_TEXT")) {
            operation = new SugiliteSetTextOperation();
            ((SugiliteSetTextOperation)operation).setText(actionParameter);
        }
        else if(actionType.equals("READ_OUT")){
            operation = new SugiliteReadoutOperation();
            ((SugiliteReadoutOperation)operation).setPropertyToReadout(actionParameter);
        }
        else {
            operation = new SugiliteUnaryOperation();
            if(actionType.equals("CLICK"))
                operation.setOperationType(SugiliteOperation.CLICK);
            else if (actionType.equals("LONG_CLICK"))
                operation.setOperationType(SugiliteOperation.LONG_CLICK);
            else if (actionType.equals("SPECIAL_GO_HOME"))
                operation.setOperationType(SugiliteOperation.SPECIAL_GO_HOME);
        }
        //TODO: disable edit for those without feature pack;
        operationBlock.setFeaturePack(null);
        if(filter != null)
            operationBlock.setElementMatchingFilter(filter.toUIElementMatchingFilter());
        operationBlock.setOperation(operation);
        if(nextBlock != null)
            operationBlock.setNextBlock(nextBlock.toSugiliteOperationBlock(context));
        operationBlock.setPreviousBlock(null);
        operationBlock.setDescription(generator.generateReadableDescription(operationBlock));
        return operationBlock;
    }

    String actionType, actionParameter;
    SugiliteFilterJSON filter;
    SugiliteOperationBlockJSON nextBlock;
    List<String> childTexts;
    long createdTime;
}
