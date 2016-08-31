package cn.edu.seu.kse.anubis.lpmln.translator;

import cn.edu.seu.kse.anubis.lpmln.model.Rule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringJoiner;

/**
 * Created by 王彬 on 2016/8/30.
 */
public class ASPTranslator extends BaseTranslator {

    @Override
    public String translateSoftRule(Rule rule) {
        StringBuilder sb=basicTranslate(rule);
        sb.append(translateCountingPart(rule,true));
        return sb.toString();
    }

    @Override
    public String translateHardRule(Rule rule) {
        StringBuilder sb=basicTranslate(rule);
        sb.append(translateCountingPart(rule,false));
        return sb.toString();
    }

    protected StringBuilder basicTranslate(Rule rule){
        StringBuilder sb=new StringBuilder();
        sb.append(translateGenerationPart(rule));
        sb.append(translateTestPart(rule));
        return sb;
    }

    protected String translateDeclarationPart(HashSet<String> hbu){
        StringBuilder sb=new StringBuilder();
        for(String hb : hbu){
            sb.append("hbu(").append(hb).append(").").append(System.lineSeparator());
        }
        return sb.toString();
    }

    protected String translateGenerationPart(Rule rule){
        StringBuilder sb=new StringBuilder();

        sb.append("apply(").append(rule.getRuleLabel()).append(")");
        sb.append(" | -apply(").append(rule.getRuleLabel()).append(") ");

        HashSet<String> vars=rule.getVars();
        int cnt=0;
        int size=vars.size()-1;

        if(size>=0){
            sb.append(":- ");
        }

        for(String v:vars){
            sb.append("hbu(").append(v).append(")");
            if(cnt!=size){
                sb.append(", ");
            }
            cnt++;
        }
        sb.append(". ").append(System.lineSeparator());

        sb.append(rule.getText()).append(" apply(").append(rule.getRuleLabel()).append(").");
        sb.append(System.lineSeparator());

        return sb.toString();
    }

    protected String translateTestPart(Rule rule){
        StringBuilder sb=new StringBuilder();
        String sat="sat("+rule.getRuleLabel()+")";
        String head="h("+rule.getRuleLabel()+")";
        String body="b("+rule.getRuleLabel()+")";
        String apply="apply("+rule.getRuleLabel()+")";

        sb.append(":- ").append(sat).append(", -").append(apply).append(".");
        sb.append(System.lineSeparator());
        sb.append(sat).append(" :- ").append(head).append(", ").append(body).append(".");
        sb.append(System.lineSeparator());
        sb.append(sat).append(" :- not ").append(body).append(", ").append(apply).append(".");
        sb.append(System.lineSeparator());
        sb.append(sat).append(" :- not ").append(body).append(", -").append(apply).append(".");
        sb.append(System.lineSeparator());
        if(rule.getBody().equals("")){
            sb.append(body).append(".");
        }else {
            sb.append(body).append(" :- ").append(rule.getBody()).append(".");
        }

        sb.append(System.lineSeparator());

        List<String> heads= rule.getHead();
        for(String h:heads){
            sb.append(head).append(" :- ").append(h).append(", ").append(apply).append(".");
            sb.append(System.lineSeparator());
            sb.append(head).append(" :- ").append(h).append(", -").append(apply).append(".");
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    protected String translateCountingPart(Rule rule, boolean isSoft){
        StringBuilder sb=new StringBuilder();
        sb.append(":~ sat(").append(rule.getRuleLabel()).append("). ");
        sb.append("[");
        if(isSoft){
            int weight= (int) (rule.getWeight()*factor);
            sb.append(weight).append("@0, ");
        }else {
            sb.append("1@1, ");
        }
        sb.append(rule.getId());
        generateVarString(rule.getVars(),sb);
        sb.append("]").append(System.lineSeparator());
        return sb.toString();
    }

    protected void generateVarString(HashSet<String> vars, StringBuilder sb){
        for(String v:vars){
            sb.append(", ").append(v);
        }
    }

    public int getFactor() {
        return factor;
    }

    public void setFactor(int factor) {
        this.factor = factor;
    }

    public void setHerbrandUniverse(HashSet<String> herbrandUniverse){
        this.herbrandUniverse=herbrandUniverse;
    }

}