package edu.cmu.hcii.sugilite.ontology.description;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.Html;
import android.text.TextUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.*;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.model.operation.SugiliteOperation;
import edu.cmu.hcii.sugilite.ontology.OntologyQuery;
import edu.cmu.hcii.sugilite.ontology.OntologyQueryFilter;
import edu.cmu.hcii.sugilite.ontology.SerializableOntologyQuery;
import edu.cmu.hcii.sugilite.ontology.SugiliteEntity;
import edu.cmu.hcii.sugilite.ontology.SugiliteRelation;

/**
 * Created by Wanling Ding on 22/02/2018.
 */

public class OntologyDescriptionGenerator {
    Context context;
    PackageManager packageManager;


    public OntologyDescriptionGenerator(Context context) {
        this.context = context;
        if(context != null) {
            this.packageManager = context.getPackageManager();
        }
    }

    // get the package name for the application
    private String getAppName(String packageName) {
        if(packageName.equals("com.android.launcher3") ||
                packageName.equals("com.google.android.googlequicksearchbox"))
            return "Home Screen";
        if(packageManager != null) {
            ApplicationInfo ai;
            try {
                ai = packageManager.getApplicationInfo(packageName, 0);
            } catch (final PackageManager.NameNotFoundException e) {
                ai = null;
            }
            final String applicationName = (String) (ai != null ? packageManager.getApplicationLabel(ai) : "(unknown)");
            return applicationName;
        }
        else {
            return packageName;
        }
    }

    // get a more viewable list order
    private String numberToOrder(String num) {
        if (num.endsWith("1")) {
            if (num.endsWith("11"))
                num = num + "th";
            else
                num = num + "st";
        }
        else if (num.endsWith("2")) {
            if (num.endsWith("12"))
                num = num + "th";
            else
                num = num + "nd";
        }
        else if (num.endsWith("3")) {
            if (num.endsWith("13"))
                num = num + "th";
            else
                num = num + "rd";
        }
        else
            num = num + "th";
        return num;
    }


    public static String setColor(String message, String color) {
        return "<font color=\"" + color + "\"><b>" + message + "</b></font>";
    }

    private String formatting(SugiliteRelation sr, String[] os) {
        if(sr == null){
            return "NULL";
        }
        if (sr.equals(SugiliteRelation.HAS_SCREEN_LOCATION) || sr.equals(SugiliteRelation.HAS_PARENT_LOCATION))
            return DescriptionGenerator.getDescription(sr) + setColor("(" + os[0] + ")", Const.SCRIPT_IDENTIFYING_FEATURE_COLOR);

        else if (sr.equals(SugiliteRelation.HAS_TEXT) ||
                sr.equals(SugiliteRelation.HAS_CONTENT_DESCRIPTION) ||
                sr.equals(SugiliteRelation.HAS_CHILD_TEXT) ||
                sr.equals(SugiliteRelation.HAS_SIBLING_TEXT) ||
                sr.equals(SugiliteRelation.HAS_VIEW_ID)) {
            return DescriptionGenerator.getDescription(sr) + setColor("\"" +  os[0] + "\"", Const.SCRIPT_IDENTIFYING_FEATURE_COLOR);
        }

        else if (sr.equals(SugiliteRelation.HAS_LIST_ORDER) || sr.equals(SugiliteRelation.HAS_PARENT_WITH_LIST_ORDER)) {
            os[0] = numberToOrder(os[0]);
            return String.format(DescriptionGenerator.getDescription(sr), setColor(os[0], Const.SCRIPT_IDENTIFYING_FEATURE_COLOR));
        }
        return DescriptionGenerator.getDescription(sr) + setColor(os[0], Const.SCRIPT_IDENTIFYING_FEATURE_COLOR);
    }


    public String getDescriptionForOperation(SugiliteOperation operation, SerializableOntologyQuery sq){
        //TODO: temporily disable because of crashes due to unable to handle filters
        //return sq.toString();
        String prefix = "";

        if(operation.getOperationType() == SugiliteOperation.CLICK){
            return prefix + getDescriptionForOperation(setColor("Click on ", Const.SCRIPT_ACTION_COLOR), sq);
        }
        else if(operation.getOperationType() == SugiliteOperation.READ_OUT){
            return prefix + getDescriptionForOperation(setColor("Read out ", Const.SCRIPT_ACTION_COLOR), sq);
        }
        else if(operation.getOperationType() == SugiliteOperation.SET_TEXT){
            return prefix + getDescriptionForOperation(setColor("Set text ", Const.SCRIPT_ACTION_COLOR), sq);
        }
        else if(operation.getOperationType() == SugiliteOperation.READOUT_CONST){
            return prefix + getDescriptionForOperation(setColor("Read out constant ", Const.SCRIPT_ACTION_COLOR), sq);
        }
        else if(operation.getOperationType() == SugiliteOperation.LOAD_AS_VARIABLE){
            return prefix + getDescriptionForOperation(setColor("Set variable to the following: ", Const.SCRIPT_ACTION_COLOR), sq);
        }
        else{
            //TODO: handle more types of operations ***
            return null;
        }

    }

    private boolean isListOrderRelation(SugiliteRelation r)
    {
        if (r!=null) {
            if (r.equals(SugiliteRelation.HAS_LIST_ORDER) || r.equals(SugiliteRelation.HAS_PARENT_WITH_LIST_ORDER)) {
                return true;
            }
        }
        return false;
    }

    private String getDescriptionForOperation(String verb, SerializableOntologyQuery sq){
        return verb + getDescriptionForOntologyQuery(sq);
    }

    private String translateFilter(OntologyQueryFilter f)
    {
        SugiliteRelation filterR = null;
        String translation = "";
        filterR = f.getRelation();
        if (isListOrderRelation(filterR)) {
            translation = String.format(DescriptionGenerator.getDescription(filterR), FilterTranslation.getFilterTranslation(f));
        }
        else {
            translation = ("the " + FilterTranslation.getFilterTranslation(f) + " " + DescriptionGenerator.getDescription(filterR)).trim();
        }
        return translation;
    }

    // separating or from and
    private String translationWithRelationshipOr(String[] args, OntologyQuery[] queries, OntologyQueryFilter f) {
        String result = "";
        int argL = args.length;
        String translatedFilter = "";
        if(f != null) {
            translatedFilter = translateFilter(f);
            translatedFilter = setColor(translatedFilter, Const.SCRIPT_ACTION_PARAMETER_COLOR);
        }
        String conjunction = "has ";
        result = "the item that ";

        for (int i = 0; i < argL-1; i++) {
            if (queries[i].getR().equals(SugiliteRelation.HAS_CLASS_NAME)||isListOrderRelation(queries[i].getR())) {
                // e.g. is button / is the first item
                conjunction = "is ";
            }
            else {
                // e.g. has text hello
                conjunction = "has ";
            }
            result += conjunction + args[i] + " or ";
        }
        if (queries[argL-1].getR().equals(SugiliteRelation.HAS_PACKAGE_NAME)) {
            // e.g. is in homescreen
            conjunction = "is ";
        }
        else {
            conjunction = "has ";
        }
        result += conjunction+args[argL-1];
        if (f != null)
            result += ", with " + translatedFilter;

        return result;
    }

    private String listOrderTranslation(String result, String translation, String[] args)
    {
        result = "";
        result += translation.replace("item", "");
        result += args[0];
        return result;
    }

    private String packageNameSpecialCaseHandler(String[] args, OntologyQueryFilter f, String result, String translatedFilter, boolean isListOrder, SugiliteRelation lastR, int argL, boolean needsMoreChange)
    {

        if (lastR != null && lastR.equals(SugiliteRelation.HAS_PACKAGE_NAME)) {
            if (!needsMoreChange)
            {
                result += " " + args[argL-1];
            }
            else
            {
                result += " that has " + args[argL-2];
                result += " " + args[argL-1];
            }
        } else {
            if (!needsMoreChange)
            {
                result += " that has " + args[argL-1];
            }
            else
            {
                result += " that has " + args[argL-2];
                result += " and " + args[argL-1];
            }
        }
        if (f != null && !isListOrder)
            result += " with " + translatedFilter;
        return result;
    }

    private String packageNameHandlerWithForLoop(String[] args, OntologyQueryFilter f, String result, String translatedFilter, boolean isListOrder, SugiliteRelation lastR, int argL, int num)
    {
        if (lastR != null && lastR.equals(SugiliteRelation.HAS_PACKAGE_NAME)) {
            for (int i = num; i < argL - 2; i++) {
                result += ", " + args[i];
            }
            result += " and " + args[argL - 2];
            result += " " + args[argL - 1];

        } else {
            for (int i = num; i < argL - 1; i++) {
                result += ", " + args[i];
            }
            result += " and " + args[argL - 1];

        }
        if (f != null && !isListOrder)
            result += " with " + translatedFilter;
        return result;
    }

    private String translationWithRelationshipAnd(String[] args, OntologyQuery[] queries, OntologyQueryFilter f) {
        String result = "";
        int argL = args.length;
        int queryL = queries.length;
        SugiliteRelation firstR = queries[0].getR();
        SugiliteRelation lastR = queries[queryL-1].getR();
        SugiliteRelation filterR = null;
        String translatedFilter = "";
        boolean isListOrder = false;
        if(f != null) {
            filterR = f.getRelation();
            translatedFilter = translateFilter(f);
            translatedFilter = setColor(translatedFilter, Const.SCRIPT_ACTION_PARAMETER_COLOR);
        }
        if (firstR != null && firstR.equals(SugiliteRelation.HAS_CLASS_NAME)) {
            SugiliteRelation secondR = queries[1].getR();
            // special case: class + list order
            if (secondR != null && isListOrderRelation(secondR)) {
                result += args[1].replace("item", "");
                result += args[0];
                if (argL == 2) {
                    if (f != null) {
                        if (isListOrderRelation(filterR)) {

                            result = listOrderTranslation(result, translatedFilter, args);
                            isListOrder = true;
                        }
                        else
                            result += " with " + translatedFilter;
                    }
                }
                else {
                    if (f != null) {
                        if (isListOrderRelation(filterR)) {
                            result = listOrderTranslation(result, translatedFilter, args);
                            isListOrder = true;
                        }
                    }
                    if (argL == 3) {
                        result = packageNameSpecialCaseHandler(args, f, result, translatedFilter, isListOrder, lastR, argL, false);
                    }
                    else if (argL == 4) {
                        result = packageNameSpecialCaseHandler(args, f, result, translatedFilter, isListOrder, lastR, argL, true);
                    }
                    // general case
                    else if (argL > 4) {
                        result += " that has ";
                        result += args[2];
                        result = packageNameHandlerWithForLoop(args, f, result, translatedFilter, isListOrder, lastR, argL, 3);
                    }
                }
            }
            // special case: only class, no list order
            else {

                if (f != null) {
                    if (isListOrderRelation(filterR))
                    {
                        result = listOrderTranslation(result, translatedFilter, args);
                        isListOrder = true;
                    }
                    else
                        result = String.format("the %s", args[0]);
                } else
                    result = String.format("the %s", args[0]);

                if (argL == 2) {
                    result = packageNameSpecialCaseHandler(args,f,result,translatedFilter,isListOrder,lastR,2,false);
                }
                else if (argL == 3) {
                    result = packageNameSpecialCaseHandler(args,f,result,translatedFilter,isListOrder,lastR,3,true);
                }
                // general case
                else if (argL > 3) {
                    result += " that has ";
                    result += args[1];
                    result = packageNameHandlerWithForLoop(args,f,result,translatedFilter,isListOrder,lastR,argL,2);
                }
            }
        }

        // special case: only list order, no class
        else if (firstR != null && isListOrderRelation(firstR)) {

            result += args[0];
            if (argL==2)
            {
                result = packageNameSpecialCaseHandler(args,f,result,translatedFilter,isListOrder,lastR,2,false);
            }
            else {
                if (f != null) {
                    if (isListOrderRelation(filterR)) {
                        result = translatedFilter;
                        isListOrder = true;
                    }
                }
                if (argL == 3) {
                    result = packageNameSpecialCaseHandler(args, f, result, translatedFilter, isListOrder, lastR, 3, true);
                }
                // general case
                if (argL > 3) {
                    result += " that has ";
                    result += args[1];
                    result = packageNameHandlerWithForLoop(args, f, result, translatedFilter, isListOrder, lastR, argL, 2);
                }
            }
        }

        // general case
        else {

            if (f != null)
            {
                if (isListOrderRelation(filterR))
                {
                    // e.g. the first item that has
                    result = translatedFilter + " that has ";
                    isListOrder = true;
                }
                else
                    result = "the item that has ";
            }
            else
                result = "the item that has ";
            // e.g. the item that has text hello
            result += args[0];
            result = packageNameHandlerWithForLoop(args,f,result,translatedFilter,isListOrder,lastR,argL,1);

        }

        return result;
    }

    private String descriptionForSingleQuery(OntologyQuery q) {
        String[] objectString = new String[1];
        SugiliteRelation r = q.getR();
        if(q.getObject() != null) {
            SugiliteEntity[] objectArr = q.getObject().toArray(new SugiliteEntity[q.getObject().size()]);
            if(r.equals(SugiliteRelation.HAS_CLASS_NAME)) {
                objectString[0] = ObjectTranslation.getTranslation(objectArr[0].toString());
            }
            else {
                if (r.equals(SugiliteRelation.HAS_TEXT) || r.equals(SugiliteRelation.HAS_CONTENT_DESCRIPTION) || r.equals(SugiliteRelation.HAS_CHILD_TEXT) || r.equals(SugiliteRelation.HAS_SIBLING_TEXT)) {
                    objectString[0] = objectArr[0].toString();
                }
                else if (r.equals(SugiliteRelation.HAS_PACKAGE_NAME)) {
                    objectString[0] = getAppName(objectArr[0].toString());
                }
                else {
                    objectString[0] = objectArr[0].toString();
                }
            }
        }
        return formatting(r, objectString);
    }

    private String descriptionForSingleQueryWithFilter(OntologyQuery q) {
        OntologyQueryFilter f = q.getOntologyQueryFilter();
        SugiliteRelation filterR = f.getRelation();
        String result = "";
        SugiliteRelation r = q.getR();
        if (isListOrderRelation(filterR))  {
            result += String.format(DescriptionGenerator.getDescription(filterR),FilterTranslation.getFilterTranslation(f));
            if (!(isListOrderRelation(r)))
                result += " that has "+ descriptionForSingleQuery(q);
            return result;
        }
        String translatedFilter = "the "+FilterTranslation.getFilterTranslation(f)+" "+DescriptionGenerator.getDescription(filterR);
        if (isListOrderRelation(r))
        {
            result += descriptionForSingleQuery(q);
            result += " with "+translatedFilter;
        }
        else {
            result += "the item that has " + descriptionForSingleQuery(q);
            result += " with "+translatedFilter;
        }
        return result;
    }

    private boolean isSpatialRelationship(SugiliteRelation r)
    {
        if (r.equals(SugiliteRelation.CONTAINS) || r.equals(SugiliteRelation.RIGHT) || r.equals(SugiliteRelation.LEFT) || r.equals(SugiliteRelation.ABOVE) || r.equals(SugiliteRelation.NEAR) || r.equals(SugiliteRelation.NEXT_TO))
            return true;
        return false;
    }

    private boolean isParentChildSiblingPrev(SugiliteRelation r)
    {
        if (r.equals(SugiliteRelation.HAS_PARENT) || r.equals(SugiliteRelation.HAS_CHILD) || r.equals(SugiliteRelation.HAS_SIBLING))
            return true;
        return false;
    }

    /**
     * Get the natural language description for a SerializableOntologyQuery
     * @param sq
     * @return
     */
    public String getDescriptionForOntologyQuery(SerializableOntologyQuery sq) {
        String postfix = "";

        OntologyQuery ontologyQuery = new OntologyQuery(sq);
        SugiliteRelation r = ontologyQuery.getR();
        OntologyQueryFilter f = ontologyQuery.getOntologyQueryFilter();
        if (ontologyQuery.getSubRelation() == OntologyQuery.relationType.nullR) {

            if (f == null) {
                return descriptionForSingleQuery(ontologyQuery) + postfix;
            }
            else {
                return descriptionForSingleQueryWithFilter(ontologyQuery) + postfix;
            }

        }

        OntologyQuery[] subQueryArray = ontologyQuery.getSubQueries().toArray(new OntologyQuery[ontologyQuery.getSubQueries().size()]);
        Arrays.sort(subQueryArray, RelationWeight.ontologyQueryComparator);

        //TODO: the use of "and" and "or" should be grammatically correct
        if (ontologyQuery.getSubRelation() == OntologyQuery.relationType.AND || ontologyQuery.getSubRelation() == OntologyQuery.relationType.OR || ontologyQuery.getSubRelation() == OntologyQuery.relationType.PREV) {
            int size = subQueryArray.length;
            String[] arr = new String[size];
            for (int i = 0; i < size; i++) {
                arr[i] = getDescriptionForOntologyQuery(new SerializableOntologyQuery(subQueryArray[i]));
            }

            if (ontologyQuery.getSubRelation() == OntologyQuery.relationType.AND) {
                return translationWithRelationshipAnd(arr,subQueryArray, f) + postfix;
            }
            else if (ontologyQuery.getSubRelation() == OntologyQuery.relationType.OR) {
                return translationWithRelationshipOr(arr, subQueryArray, f) + postfix;
            }

            else if (ontologyQuery.getSubRelation() == OntologyQuery.relationType.PREV) {
                String res = "the item that ";
                res += DescriptionGenerator.getDescription(r);
                if (isParentChildSiblingPrev(r))
                    res += "has ";
                else if (isSpatialRelationship(r))
                    res += "is ";
                res += DescriptionGenerator.getDescription(r);
                if (isSpatialRelationship(r))
                    res += "the item ";
                res += "that has " + arr[0];
                for (int i = 1; i<arr.length;i++) {
                    res += " and " + arr[i];
                }
                return res + postfix;
            }
        }

        return "NULL";
    }

    public static void main(String[] args){
        OntologyDescriptionGenerator generator = new OntologyDescriptionGenerator(null);
        System.out.println("Enter a query:");
        while (true) {
            BufferedReader screenReader = new BufferedReader(new InputStreamReader(System.in));
            String input = "";
            System.out.print("> ");
            try {
                input = screenReader.readLine();
            }
            catch (Exception e){
                e.printStackTrace();
            }
            try {
                SerializableOntologyQuery query = new SerializableOntologyQuery(OntologyQuery.deserialize(input));
                String description = generator.getDescriptionForOperation("Click on ", query);
                //clean up the html tags
                description = description.replaceAll("\\<.*?\\>", "");
                System.out.println(description);
            }
            catch (Exception e){
                System.out.println("Failed to parse the query");
                e.printStackTrace();
            }

        }
    }





}
