package cn.edu.seu.kse.lpmln.grounder;

import cn.edu.seu.kse.lpmln.exception.solveexception.SolveException;
import cn.edu.seu.kse.lpmln.model.LpmlnProgram;
import cn.edu.seu.kse.lpmln.translator.impl.LPMLN2ASPTranslator;
import cn.edu.seu.kse.lpmln.util.FileHelper;
import cn.edu.seu.kse.lpmln.util.commandline.CommonCmd;
import cn.edu.seu.kse.lpmln.util.syntax.SyntaxModule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author 许鸿翔
 * @date 2018/5/22
 */
public class GringoGrounder implements LPMLNGrounder{
    public static final String cmdPrefix = "gringo -t --keep-facts ";
    public static final String GET = ":-";
    public static final String NOTUNSAT = "not unsat(";

    @Override
    public String grounding(File fileToGround) {
        StringBuilder groundProgram = new StringBuilder();
        CommonCmd cmd = new CommonCmd();
        File randomFile = FileHelper.randomFile();
        String translated = translate(fileToGround);
        FileHelper.writeFile(randomFile,translated);
        cmd.call(cmdPrefix+randomFile.getAbsoluteFile());
        List<String> rules = Arrays.asList(cmd.getOutput().toString().split("\r\n"));
        rules.forEach(rule->{
            if(rule.startsWith("#")){
                //metarule
                groundProgram.append(rule).append("\r\n");
            }
            StringBuilder toAppend = new StringBuilder();
            int idxUnsat = rule.indexOf(NOTUNSAT);
            int idxGet = rule.indexOf(GET);
            if(idxUnsat>0&&idxUnsat>idxGet){
                //从unsat谓词提取权重
                String weightList = rule.substring(idxUnsat+NOTUNSAT.length(),rule.indexOf(")",idxUnsat));
                String[] params = weightList.split(",");

                //添加权重
                if(Integer.valueOf(params[params.length-2])==2){
                    //强规则
                }else if(Integer.valueOf(params[params.length-2])==1){
                    //弱规则
                    toAppend.append(params[params.length-1]);
                    toAppend.append(" : ");
                }else{
                    throw new SolveException("rule param fail: "+rule);
                }
                rule = rule.substring(0,idxUnsat).trim();
                if(rule.endsWith(",")){
                    toAppend.append(rule.substring(0, rule.length()-1));
                }else if(rule.endsWith(":-")){
                    toAppend.append(rule.substring(0, rule.length()-2));
                }else{
                    throw new SolveException("rule end fail: "+rule);
                }
                toAppend.append(".\r\n");
                groundProgram.append(toAppend.toString().replaceAll(":-"," :- "));
            }
        });
        return groundProgram.toString();
    }

    private String translate(File fileToGround){
        LpmlnProgram lpmlnProgram = null;
        LPMLN2ASPTranslator translator = new LPMLN2ASPTranslator();
        translator.setWeakTranslate(false);
        try {
            lpmlnProgram = SyntaxModule.parseLPMLN(fileToGround);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return translator.translate(lpmlnProgram);
    }
}
