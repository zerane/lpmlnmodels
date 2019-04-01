package cn.edu.seu.kse.lpmln.solver.impl;

import cn.edu.seu.kse.lpmln.model.*;
import cn.edu.seu.kse.lpmln.util.UnionFindSet;

import java.util.*;

import static cn.edu.seu.kse.lpmln.util.CommonStrings.*;
import static cn.edu.seu.kse.lpmln.util.LpmlnProgramHelper.*;

/**
 * Conflict-Driven Disjunctive Answer Set Solving
 * CDNL:conflict driven nogood learning
 * @author 许鸿翔
 * @date 2019/3/27
 */
public class LPMLNCDNLSolver extends LPMLNBaseSolver{
    /**
     * T:in
     * F:not in
     * no key:unassigned
     */
    private Map<String,Boolean> assignment = new HashMap<>();

    /**
     * 剩余需要分配的原子
     */
    private Set<String> toassign = new HashSet<>();

    /**
     * 标识当前是否冲突
     */
    private boolean conflict = false;

    /**
     * \delta
     */
    private Nogood conflictNogood = null;

    private String conflictSigma = null;

    /**
     * decision level
     */
    private int dl = 0;

    /**
     * unfounded set
     */
    private Set<String> u = new HashSet<>();

    /**
     * C_{\Pi}
     */
    private List<Set<String>> cPi = new ArrayList<>();

    /**
     * accessor
     */
    private UnionFindSet<String> cPiAccessor;

    /**
     * C_{\Pi}^{\vee}
     */
    private List<Set<String>> cPi_HCF = new ArrayList<>();


    /**
     * \Delta_{\Pi}
     * 规则满足，包括体部成立辅助谓词
     * +
     * \Theta_{\overrightarrow{\Pi}}
     * 原子支持，包括支持辅助谓词
     */
    private List<Nogood> nogoodCompletion = new ArrayList<>();

    /**
     * \Theta_{\overrightarrow{\Pi}}
     * 动态nogood
     */
    private List<Nogood> nogoodDynamic = new ArrayList<>();


    /**
     * 文字到原子支持的下标
     */
    private Map<String,List<Integer>> ltnCompletion = new HashMap<>();

    /**
     * 文字到动态nogood的下标
     */
    private Map<String,List<Integer>> ltnDynamic = new HashMap<>();

    private List<Rule> rules;

    private Set<String> literals = new HashSet<>();

    private Map<String,List<String>> supporters;

    private Map<String,Integer> dlMap = new HashMap<>();

    private LinkedList<String> assignStack = new LinkedList<>();

    private Map<String,Integer> stackPosition = new HashMap<>();

    /**
     * \sigma
     */
    private List<SignedLiteral> resultUnits = new LinkedList<>();

    public WeightedAnswerSet getSingleAs(LpmlnProgram program){
        this.lpmlnProgram = program;
        init();
        while(true){
            propagation();
            if(conflict){
                if(dl==0){
                    return null;
                }
                analysisAndUndo();
            }else if(toassign.size()==0){
                return generate();
            }
            else{
                assert false;
            }
        }
    }

    /**
     * 1.传播原子，传播到不动点
     * 2.扩展nogood
     * 所有出参都以成员变量表示，调用前注意处理
     * input:   \Pi \nabla \A
     * output:  A \nabla
     */
    private void propagation(){
        while(true){
            while(resultUnits.size()>0){
                for (SignedLiteral unit: resultUnits) {
                    Boolean cur = assignment.get(unit.getLiteral());
                    if(cur==null){
                        assign(unit.getLiteral(),unit.isSign());
                        //还没分配
                        getResultUnit(unit.getLiteral(),ltnCompletion,nogoodCompletion);
                        if(conflict){
                            return;
                        }
                        getResultUnit(unit.getLiteral(),ltnDynamic,nogoodDynamic);
                        if(conflict){
                            return;
                        }
                    }else if(!cur.equals(unit.isSign())){
                        assert false;
                        //分配冲突
                        conflict = true;
                        return;
                    }
                }
            }

            if(cPi.size()==0){
                return;
            }

            u.removeIf(p->assignment.containsKey(p)&& !assignment.get(p));

            if(u.size()==0){
                unfoundedSet();
            }

            u.forEach(lit->{
                resultUnits.add(new SignedLiteral(lit,false));
            });
            //TODO:这里有没有必要更新动态nogood？
        }
    }

    /**
     * 分配使用此函数，处理首尾
     * @param lit 文字
     * @param val TF
     */
    private void assign(String lit,boolean val){
        assignment.put(lit,val);
    }

    /**
     * 计算unfoundedSet
     */
    private void unfoundedSet(){
        //这里u应该是空集
        assert u.size()==0;
        List<String> unfounded = new ArrayList<>();
        toassign.forEach(lit->{
            for (String sup : supporters.get(lit)) {
                if(assignment.get(sup)==null||assignment.get(sup)){
                    return;
                }
            }
            unfounded.add(lit);
        });

        if(unfounded.size()==0){
            return;
        }

        String root = cPiAccessor.find(unfounded.get(0));

        unfounded.forEach(lit->{
            if(cPiAccessor.find(lit).equals(root)){
                u.add(lit);
            }
        });
    }

    /**
     * 计算ru
     * @param key 查找索引，文字
     * @param map 从哪些nogood里找
     * @param nogoodList map对应的nogoodlist
     */
    private void getResultUnit(String key,Map<String,List<Integer>> map,List<Nogood> nogoodList){
        for (Integer idx : map.get(key)) {
            Nogood tocheck = nogoodList.get(idx);
            SignedLiteral ru = tocheck.getResultUnit(assignment);
            if(ru!=null){
                if(ru.getLiteral().equals(EXT_FALSE)){
                    conflictNogood = tocheck;
                    conflictSigma = key;
                    conflict = true;
                    return;
                }
                resultUnits.add(new SignedLiteral(ru.getLiteral(),!ru.isSign()));
            }
        }
    }

    private void init(){
        assignment = new HashMap<>();
        toassign = new HashSet<>();
        conflict = false;
        dl = 0;
        conflictNogood = null;
        u = new HashSet<>();
        cPi = new ArrayList<>();
        rules = lpmlnProgram.getRules();

        initCPi();

        initLiterals();

        initNogood();
    }

    private void initCPi(){
        cPi.clear();
        Map<String,Set<String>> reachable = dependToReachable(getLiteralPostiveDependency(lpmlnProgram));
        cPiAccessor = reachableToUfs(reachable);
        Set<Set<String>> loops = ufsToLitSets(reachable,cPiAccessor);
        loops.removeIf(loop -> loop.size() < 2);
        cPi.addAll(loops);
    }

    private void initLiterals(){
        rules.forEach(r->{
            literals.addAll(r.getHead());
            literals.addAll(r.getPositiveBody());
            r.getNegativeBody().forEach(nb-> literals.add(nb.substring(NOT.length())));
        });
    }

    private void initNogood(){
        //initRuleSatisfy();
        initComp();
    }

    private void constructMap(List<Nogood> nogoods,Map<String,List<Integer>> map){
        for(int i=0;i<nogoods.size();i++){
            Nogood nogood = nogoods.get(i);
            for (String k : nogood.getKeySet()) {
                List<Integer> idxs;
                if(map.containsKey(k)){
                    idxs = map.get(k);
                }else{
                    idxs = new ArrayList<>();
                    map.put(k,idxs);
                }
                idxs.add(i);
            }
        }
    }

    private void initComp(){
        nogoodCompletion.clear();
        ltnCompletion.clear();
        supporters = new HashMap<>(literals.size());
        literals.forEach(lit->supporters.put(lit,new ArrayList<>()));

        for(int i=0;i<rules.size();i++){
            Rule r = rules.get(i);
            List<String> head = r.getHead();
            for (String curLit : head) {
                String sup = getAtomSupport(i, curLit);
                supporters.get(curLit).add(sup);

                //支持辅助谓词
                //项都成立但支持不成立
                Nogood n1 = new Nogood();
                n1.add(sup, false);
                r.getPositiveBody().forEach(pb -> n1.add(pb, false));
                r.getPositiveBody().forEach(nb -> n1.add(nb.substring(NOT.length()), true));
                r.getHead().forEach(h -> {
                    if (!h.equals(curLit)) {
                        n1.add(h, true);
                    }
                });
                nogoodCompletion.add(n1);
                //支持成立但是有项不成立
                r.getPositiveBody().forEach(pb -> {
                    Nogood n2 = new Nogood();
                    n2.add(sup, true);
                    n2.add(pb, false);
                    nogoodCompletion.add(n2);
                });
                r.getNegativeBody().forEach(nb -> {
                    Nogood n2 = new Nogood();
                    n2.add(sup, true);
                    n2.add(nb, true);
                    nogoodCompletion.add(n2);
                });

                //规则满足
                r.getHead().forEach(h -> {
                    Nogood n3 = new Nogood();
                    n3.add(sup, true);
                    //TODO:考虑头部not
                    n3.add(h, false);
                    nogoodCompletion.add(n3);
                });
            }
        }

        //原子支持
        supporters.forEach((lit,supporter)->{
            Nogood n4 = new Nogood();
            n4.add(lit,true);
            supporter.forEach(p->n4.add(p,false));
            nogoodCompletion.add(n4);
        });

        constructMap(nogoodCompletion,ltnCompletion);
    }

    private String getAtomSupport(int i,String atom){
        return EXT+i+SUP+atom;
    }

    /**
     * 所有出参都以成员变量表示，调用前注意处理
     * input:   \delta \Pi \nabla \A
     * output:  \varepsilon \k
     */
    private void analysisAndUndo(){
        analysis();
        //dl提前赋值
        while(dlLevel(assignStack.peek())>dl){
            resign(assignStack.poll());
        }
    }

    private void resign(String lit){
        assignment.remove(lit);
    }

    /**
     * conflict analysis
     */
    private void analysis(){
        PriorityQueue<SignedLiteral> delta = new PriorityQueue<>((o1, o2) -> stackPosition.get(o2.getLiteral())-stackPosition.get(o1.getLiteral()));
        delta.addAll(conflictNogood.getSignedLiterals());
        int k;
        while(true){
            //TODO:这边方式可能需要修改下
            SignedLiteral sigma = delta.poll();
            SignedLiteral remainMax = delta.peek();
            k = dlLevel(remainMax);
            if(dlLevel(sigma) == k){
                resign(sigma.getLiteral());
                delta.addAll(findSourceNogoodItems(sigma));
            }else{
                //analysis return
                break;
            }
        }
        dl = k;
        Nogood toAdd = new Nogood();
        delta.forEach(sl->toAdd.add(sl.getLiteral(),sl.isSign()));
        nogoodDynamic.add(toAdd);
    }

    /**
     * 查找文字的来源nogood，去除文字本身后返回剩余文字列表
     * @param ru 文字
     * @return 剩余列表
     */
    private List<SignedLiteral> findSourceNogoodItems(SignedLiteral ru){
        List<SignedLiteral> result = new ArrayList<>();
        SignedLiteral temp;
        for (Integer idx : ltnCompletion.get(ru.getLiteral())) {
            Nogood cur = nogoodCompletion.get(idx);
            temp = cur.getResultUnit(assignment);
            if(ru.equals(temp)){
                cur.getSignedLiterals().forEach(s->{
                    if(!s.getLiteral().equals(ru.getLiteral())){
                        result.add(s);
                    }
                });
                return result;
            }
        }
        assert false;
        return result;
    }

    private int dlLevel(String sigma){
        return dlMap.get(sigma);
    }

    private int dlLevel(SignedLiteral sigma){
        return dlMap.get(sigma.getLiteral());
    }

//    private void cdnl(Set<String> c){
//
//    }

    private WeightedAnswerSet generate(){
        WeightedAnswerSet was = new WeightedAnswerSet();
        AnswerSet as = new AnswerSet();
        was.setAnswerSet(as);
        Integer[] ini = {0,0};
        was.setWeights(Arrays.asList(ini));
        assignment.forEach((k,v)->{
            if(v&&!k.startsWith(EXT)){
                as.add(k);
            }
        });
        return was;
    }

}