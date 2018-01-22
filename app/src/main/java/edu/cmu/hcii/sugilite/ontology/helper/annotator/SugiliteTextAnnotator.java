package edu.cmu.hcii.sugilite.ontology.helper.annotator;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.hcii.sugilite.ontology.SugiliteRelation;

/**
 * @author toby
 * @date 1/17/18
 * @time 11:37 PM
 */
public class SugiliteTextAnnotator {
    protected List<SugiliteTextAnnotator> subAnnotators;
    public SugiliteTextAnnotator(){
        this(false);
    }

    public SugiliteTextAnnotator(boolean addAllAvailableAnnotator){
        subAnnotators = new ArrayList<>();
        if(addAllAvailableAnnotator){
            //add all available annotator implementations
            addAnnotator(new EmailAddressAnnotator(),
                    new PhoneNumberAnnotator(),
                    new MoneyAnnotator());

        }
    }

    public void addAnnotator(SugiliteTextAnnotator... entityTagAnnotators){
        for(SugiliteTextAnnotator entityTagAnnotator : entityTagAnnotators) {
            subAnnotators.add(entityTagAnnotator);
        }
    }

    public List<AnnotatingResult> annotate(String text){
        List<AnnotatingResult> results = new ArrayList<>();
        for(SugiliteTextAnnotator subAnnotator : subAnnotators){
            results.addAll(subAnnotator.annotate(text));
        }
        return results;
    }

    /**
     * used for returning the results
     */
    public static class AnnotatingResult{
        private SugiliteRelation relation;
        private String matchedString;
        private int startIndex;
        private int endIndex;
        private Double numericValue;

        public AnnotatingResult(SugiliteRelation relation, String matchedString, int startIndex, int endIndex, Double numericValue){
            this.relation = relation;
            this.matchedString = matchedString;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.numericValue = numericValue;
        }

        public AnnotatingResult(SugiliteRelation relation, String matchedString, int startIndex, int endIndex){
            this(relation, matchedString, startIndex, endIndex, null);
        }

        public SugiliteRelation getRelation() {
            return relation;
        }

        public String getMatchedString() {
            return matchedString;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public Double getNumericValue() {
            return numericValue;
        }

        public void setRelation(SugiliteRelation relation) {
            this.relation = relation;
        }
    }
}
