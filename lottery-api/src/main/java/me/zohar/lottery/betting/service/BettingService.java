package me.zohar.lottery.betting.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import me.zohar.lottery.betting.domain.BettingOrder;
import me.zohar.lottery.betting.domain.BettingRecord;
import me.zohar.lottery.betting.param.BettingOrderQueryCondParam;
import me.zohar.lottery.betting.param.BettingRecordParam;
import me.zohar.lottery.betting.param.ChangeOrderParam;
import me.zohar.lottery.betting.param.PlaceOrderParam;
import me.zohar.lottery.betting.repo.BettingOrderRepo;
import me.zohar.lottery.betting.repo.BettingRecordRepo;
import me.zohar.lottery.betting.vo.BettingOrderDetailsVO;
import me.zohar.lottery.betting.vo.BettingOrderInfoVO;
import me.zohar.lottery.betting.vo.BettingRecordVO;
import me.zohar.lottery.betting.vo.WinningRankVO;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.common.vo.PageResult;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.game.domain.GamePlay;
import me.zohar.lottery.game.repo.GamePlayRepo;
import me.zohar.lottery.issue.domain.Issue;
import me.zohar.lottery.issue.enums.GamePlayEnum;
import me.zohar.lottery.issue.repo.IssueRepo;
import me.zohar.lottery.useraccount.domain.AccountChangeLog;
import me.zohar.lottery.useraccount.domain.UserAccount;
import me.zohar.lottery.useraccount.repo.AccountChangeLogRepo;
import me.zohar.lottery.useraccount.repo.UserAccountRepo;

@Validated
@Service
@Slf4j
public class BettingService {

	@Autowired
	private StringRedisTemplate template;

	@Lazy
	@Autowired
	private TrackingNumberService trackingNumberService;

	@Autowired
	private BettingOrderRepo bettingOrderRepo;

	@Autowired
	private BettingRecordRepo bettingRecordRepo;

	@Autowired
	private UserAccountRepo userAccountRepo;

	@Autowired
	private AccountChangeLogRepo accountChangeLogRepo;

	@Autowired
	private GamePlayRepo gamePlayRepo;

	@Autowired
	private IssueRepo issueRepo;

	@Transactional(readOnly = true)
	public List<WinningRankVO> findTop50WinningRank() {
		List<BettingOrder> bettingOrders = bettingOrderRepo
				.findTop50ByBettingTimeGreaterThanAndStateOrderByTotalWinningAmountDesc(DateUtil.beginOfDay(new Date()),
						Constant.投注订单状态_已中奖);
		if (bettingOrders.size() < 50) {
			bettingOrders = bettingOrderRepo.findTop50ByStateOrderByTotalWinningAmountDesc(Constant.投注订单状态_已中奖);
		}
		return WinningRankVO.convertFor(bettingOrders);
	}

	@Transactional(readOnly = true)
	public BettingOrderDetailsVO findMyBettingOrderDetails(String id, String userAccountId) {
		BettingOrderDetailsVO vo = findBettingOrderDetails(id);
		if (!userAccountId.equals(vo.getUserAccountId())) {
			throw new BizException(BizError.无权查看投注记录);
		}
		return vo;
	}

	@Transactional(readOnly = true)
	public BettingOrderDetailsVO findBettingOrderDetails(String id) {
		BettingOrder bettingOrder = bettingOrderRepo.getOne(id);
		return BettingOrderDetailsVO.convertFor(bettingOrder);
	}

	/**
	 * 分页获取我的投注订单信息
	 * 
	 * @param param
	 * @return
	 */
	@Transactional(readOnly = true)
	public PageResult<BettingOrderInfoVO> findMyBettingOrderInfoByPage(BettingOrderQueryCondParam param) {
		if (StrUtil.isBlank(param.getUserAccountId())) {
			throw new BizException(BizError.无权查看投注记录);
		}
		return findBettingOrderInfoByPage(param);
	}

	/**
	 * 分页获取投注订单信息
	 * 
	 * @param param
	 * @return
	 */
	@Transactional(readOnly = true)
	public PageResult<BettingOrderInfoVO> findBettingOrderInfoByPage(BettingOrderQueryCondParam param) {
		Specification<BettingOrder> spec = new Specification<BettingOrder>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<BettingOrder> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (StrUtil.isNotEmpty(param.getOrderNo())) {
					predicates.add(builder.equal(root.get("orderNo"), param.getOrderNo()));
				}
				if (StrUtil.isNotEmpty(param.getGameCode())) {
					predicates.add(builder.equal(root.get("gameCode"), param.getGameCode()));
				}
				if (param.getStartTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("bettingTime").as(Date.class),
							DateUtil.beginOfDay(param.getStartTime())));
				}
				if (param.getEndTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("bettingTime").as(Date.class),
							DateUtil.endOfDay(param.getEndTime())));
				}
				if (StrUtil.isNotEmpty(param.getState())) {
					predicates.add(builder.equal(root.get("state"), param.getState()));
				}
				if (StrUtil.isNotEmpty(param.getUserAccountId())) {
					predicates.add(builder.equal(root.get("userAccountId"), param.getUserAccountId()));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<BettingOrder> result = bettingOrderRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("bettingTime"))));
		PageResult<BettingOrderInfoVO> pageResult = new PageResult<>(BettingOrderInfoVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

	/**
	 * 获取当天最新5次投注记录
	 */
	@Transactional(readOnly = false)
	public List<BettingRecordVO> findTodayLatestThe5TimeBettingRecord(String userAccountId, String gameCode) {
		Date bettingTime = DateUtil.beginOfDay(new Date());
		List<BettingRecord> bettingRecords = bettingRecordRepo
				.findTop5ByBettingOrder_UserAccountIdAndBettingOrder_GameCodeAndBettingOrder_BettingTimeGreaterThanEqualOrderByBettingOrder_BettingTimeDesc(
						userAccountId, gameCode, bettingTime);
		return BettingRecordVO.convertFor(bettingRecords);
	}

	@ParamValid
	@Transactional
	public String placeOrder(PlaceOrderParam placeOrderParam, String userAccountId) {
		Date now = new Date();
		Issue currentIssue = issueRepo.findTopByGameCodeAndStartTimeLessThanEqualAndEndTimeGreaterThan(
				placeOrderParam.getGameCode(), now, now);
		Issue bettingIssue = issueRepo.findByGameCodeAndIssueNum(placeOrderParam.getGameCode(),
				placeOrderParam.getIssueNum());
		if (currentIssue == null) {
			throw new BizException(BizError.休市中);
		}
		if (currentIssue.getIssueNum() == placeOrderParam.getIssueNum()) {
			String gameState = template.opsForValue().get(placeOrderParam.getGameCode() + Constant.游戏当期状态);
			if (Constant.游戏当期状态_休市中.equals(gameState)) {
				throw new BizException(BizError.休市中);
			}
			if (Constant.游戏当期状态_已截止投注.equals(gameState)) {
				throw new BizException(BizError.已截止投注);
			}
			String gameCurrentIssueNum = template.opsForValue().get(placeOrderParam.getGameCode() + Constant.游戏当前期号);
			if (StrUtil.isEmpty(gameCurrentIssueNum)) {
				throw new BizException(BizError.休市中);
			}
		} else {
			if (bettingIssue == null) {
				throw new BizException(BizError.期号非法);
			}
			if (bettingIssue.getLotteryDate().getTime() < currentIssue.getLotteryDate().getTime()) {
				throw new BizException(BizError.期号非法);
			}
			if (bettingIssue.getLotteryDate().getTime() > currentIssue.getLotteryDate().getTime()) {
				throw new BizException(BizError.只能追当天的号);
			}
			if (bettingIssue.getIssueNum() < currentIssue.getIssueNum()) {
				throw new BizException(BizError.该期已封盘无法投注);
			}
		}

		long totalBettingCount = 0;
		double totalBettingAmount = 0;
		List<BettingRecord> bettingRecords = new ArrayList<>();
		for (BettingRecordParam bettingRecordParam : placeOrderParam.getBettingRecords()) {
			GamePlay gamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(placeOrderParam.getGameCode(),
					bettingRecordParam.getGamePlayCode());
			if (gamePlay == null) {
				throw new BizException(BizError.游戏玩法不存在);
			}
			if (Constant.游戏玩法状态_禁用.equals(gamePlay.getState())) {
				throw new BizException(BizError.游戏玩法被禁用);
			}
			Double odds = gamePlay.getOdds();
			if (odds == null || odds <= 0) {
				throw new BizException(BizError.玩法赔率异常);
			}
			double bettingAmount = NumberUtil.round(bettingRecordParam.getBettingCount()
					* placeOrderParam.getBaseAmount() * placeOrderParam.getMultiple(), 4).doubleValue();
			bettingRecords.add(bettingRecordParam.convertToPo(bettingAmount, odds));
			totalBettingCount += bettingRecordParam.getBettingCount();
			totalBettingAmount += bettingAmount;
		}
		UserAccount userAccount = userAccountRepo.getOne(userAccountId);
		double balance = NumberUtil.round(userAccount.getBalance() - totalBettingAmount, 4).doubleValue();
		if (userAccount.getBalance() <= 0 || balance < 0) {
			throw new BizException(BizError.余额不足);
		}

		BettingOrder bettingOrder = placeOrderParam.convertToPo(bettingIssue.getId(), totalBettingCount,
				totalBettingAmount, userAccountId);
		bettingOrderRepo.save(bettingOrder);
		for (BettingRecord bettingRecord : bettingRecords) {
			bettingRecord.setBettingOrderId(bettingOrder.getId());
			bettingRecordRepo.save(bettingRecord);
		}
		userAccount.setBalance(balance);
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithPlaceOrder(userAccount, bettingOrder));
		return bettingOrder.getId();
	}

	/**
	 * 改单
	 */
	@ParamValid
	@Transactional
	public void changeOrder(List<ChangeOrderParam> params) {
		for (ChangeOrderParam param : params) {
			BettingOrder bettingOrder = bettingOrderRepo.getOne(param.getBettingOrderId());
			GamePlay gamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(bettingOrder.getGameCode(),
					param.getGamePlayCode());
			if (gamePlay == null) {
				throw new BizException(BizError.游戏玩法不存在);
			}
			Double odds = gamePlay.getOdds();
			if (odds == null || odds <= 0) {
				throw new BizException(BizError.玩法赔率异常);
			}

			BettingRecord bettingRecord = bettingRecordRepo.getOne(param.getBettingRecordId());
			bettingRecord.setGamePlayCode(gamePlay.getGamePlayCode());
			bettingRecord.setOdds(gamePlay.getOdds());
			bettingRecord.setSelectedNo(param.getSelectedNo());
			bettingRecordRepo.save(bettingRecord);
		}
	}

	/**
	 * 结算
	 */
	@Transactional
	public void settlement(@NotBlank String issueId) {
		Issue issue = issueRepo.getOne(issueId);
		if (issue == null || StrUtil.isEmpty(issue.getLotteryNum())) {
			log.error("当前期号还没开奖;id:{},issueNum:{}", issue.getId(), issue.getLotteryNum());
			return;
		}

		List<BettingOrder> bettingOrders = bettingOrderRepo.findByGameCodeAndIssueNumAndState(issue.getGameCode(),
				issue.getIssueNum(), Constant.投注订单状态_未开奖);
		for (BettingOrder bettingOrder : bettingOrders) {
			String state = Constant.投注订单状态_未中奖;
			double totalWinningAmount = 0;
			Set<BettingRecord> bettingRecords = bettingOrder.getBettingRecords();
			for (BettingRecord bettingRecord : bettingRecords) {
				GamePlayEnum gamePlay = GamePlayEnum
						.getPlay(bettingOrder.getGameCode() + "_" + bettingRecord.getGamePlayCode());
				int winningCount = gamePlay.calcWinningCount(issue.getLotteryNum(), bettingRecord.getSelectedNo());
				if (winningCount > 0) {
					double winningAmount = (bettingRecord.getBettingAmount() * bettingRecord.getOdds() * winningCount);
					bettingRecord.setWinningAmount(NumberUtil.round(winningAmount, 4).doubleValue());
					bettingRecord.setProfitAndLoss(
							NumberUtil.round(winningAmount - bettingRecord.getBettingAmount(), 4).doubleValue());
					bettingRecordRepo.save(bettingRecord);
					state = Constant.投注订单状态_已中奖;
					totalWinningAmount += winningAmount;
				}
			}
			bettingOrder.setLotteryNum(issue.getLotteryNum());
			bettingOrder.setState(state);
			if (Constant.投注订单状态_未中奖.equals(state)) {
				bettingOrderRepo.save(bettingOrder);
			} else {
				bettingOrder.setTotalWinningAmount(NumberUtil.round(totalWinningAmount, 4).doubleValue());
				bettingOrder.setTotalProfitAndLoss(
						NumberUtil.round(totalWinningAmount - bettingOrder.getTotalBettingAmount(), 4).doubleValue());
				bettingOrderRepo.save(bettingOrder);
				UserAccount userAccount = bettingOrder.getUserAccount();
				double balance = userAccount.getBalance() + totalWinningAmount;
				userAccount.setBalance(NumberUtil.round(balance, 4).doubleValue());
				userAccountRepo.save(userAccount);
				accountChangeLogRepo.save(AccountChangeLog.buildWithWinning(userAccount, bettingOrder));
			}
		}
	}

	/**
	 * 撤单
	 */
	@Transactional
	public void cancelOrder(@NotBlank String orderId, @NotBlank String userAccountId) {
		BettingOrder order = bettingOrderRepo.getOne(orderId);
		if (order == null) {
			throw new BizException(BizError.投注订单不存在);
		}
		if (!order.getUserAccountId().equals(userAccountId)) {
			throw new BizException(BizError.无权查看投注记录);
		}
		if (!Constant.投注订单状态_未开奖.equals(order.getState())) {
			throw new BizException(BizError.已开奖或已取消无法撤单);
		}
		Issue issue = issueRepo.findByGameCodeAndIssueNum(order.getGameCode(), order.getIssueNum());
		if (new Date().getTime() > issue.getEndTime().getTime()) {
			throw new BizException(BizError.该期已封盘无法撤单);
		}

		order.cancelOrder();
		UserAccount userAccount = order.getUserAccount();
		double balance = userAccount.getBalance() + order.getTotalBettingAmount();
		userAccount.setBalance(NumberUtil.round(balance, 4).doubleValue());
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithBettingCancelOrder(userAccount, order));
	}

	@Transactional
	public void batchCancelOrder(@NotEmpty List<String> orderIds, @NotBlank String userAccountId) {
		for (String orderId : orderIds) {
			cancelOrder(orderId, userAccountId);
		}
	}

}
