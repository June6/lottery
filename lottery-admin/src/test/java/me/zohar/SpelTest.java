package me.zohar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptException;

import org.codehaus.groovy.control.CompilationFailedException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.springframework.util.ResourceUtils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import groovy.lang.GroovyShell;
import me.zohar.lottery.information.vo.LotteryInformationVO;

public class SpelTest {

	@Test
	public void test2() throws ScriptException, CompilationFailedException, IOException {
		File file = ResourceUtils.getFile("classpath:test.groovy");
		GroovyShell shell = new GroovyShell();
		@SuppressWarnings("unchecked")
		List<LotteryInformationVO> vos = (List<LotteryInformationVO>) shell.evaluate(file);
		System.out.println(vos.size());
		// for (int i = 0; i < 100; i++) {
		// Map<String, Object> map = new HashMap<>();
		// shell.setVariable("map", map);
		// map.put("a", i);
		// Object eval2 = shell.evaluate("System.out.println(map);return map;");
		// //System.out.println(eval2);
		// }
	}

	@Test
	public void test() throws IOException {
		testInner();
	}

	public List<LotteryInformationVO> testInner() throws IOException {
		List<LotteryInformationVO> vos = new ArrayList<>();
		String url = "http://www.zhcw.com/xinwen/caizhongxinwen/";
		Elements elements = Jsoup.connect(url).get().getElementsByClass("Nlistul").first().getElementsByTag("li");
		for (Element element : elements) {
			Element timeTag = element.getElementsByClass("Ntime").first();
			if (timeTag == null) {
				continue;
			}
			Element urlTag = element.getElementsByTag("a").first();
			String newUrl = "http://www.zhcw.com" + urlTag.attr("href");
			Document document = Jsoup.connect(newUrl).get();
			if (document == null) {
				continue;
			}
			String title = document.getElementsByClass("newsTitle").first().text();
			String content = document.getElementById("news_content").html();
			String[] split = document.getElementsByClass("message").first().text().split(" ");
			String createDate = split[0] + " " + split[1];
			LotteryInformationVO vo = new LotteryInformationVO();
			vo.setTitle(title);
			vo.setContent(content);
			vo.setCreateTime(DateUtil.parse(createDate, DatePattern.NORM_DATETIME_PATTERN));
			vo.setPublishTime(vo.getCreateTime());
			vos.add(vo);
		}
		return vos;
	}

}
