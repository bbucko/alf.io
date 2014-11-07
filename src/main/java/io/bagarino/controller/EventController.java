/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.controller;


import io.bagarino.controller.decorator.EventDescriptor;
import io.bagarino.controller.decorator.SaleableTicketCategory;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.Event;
import io.bagarino.model.SpecialPrice;
import io.bagarino.model.SpecialPrice.Status;
import io.bagarino.model.modification.support.LocationDescriptor;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.SpecialPriceRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.user.OrganizationRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static io.bagarino.model.system.ConfigurationKeys.MAPS_CLIENT_API_KEY;
import static io.bagarino.model.system.ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION;
import static io.bagarino.util.OptionalWrapper.optionally;

@Controller
public class EventController {

    private static final String REDIRECT = "redirect:";
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final SpecialPriceRepository specialPriceRepository;

	@Autowired
	public EventController(ConfigurationManager configurationManager,
			TicketRepository ticketRepository,
			EventRepository eventRepository,
			OrganizationRepository organizationRepository,
			TicketCategoryRepository ticketCategoryRepository,
			SpecialPriceRepository specialPriceRepository) {
		this.configurationManager = configurationManager;
		this.ticketRepository = ticketRepository;
		this.eventRepository = eventRepository;
		this.organizationRepository = organizationRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.specialPriceRepository = specialPriceRepository;
	}

	@RequestMapping(value = {"/"}, method = RequestMethod.GET)
	public String listEvents(Model model) {
		List<Event> events = eventRepository.findAll();
		if(events.size() == 1) {
			return REDIRECT + "/event/" + events.get(0).getShortName() + "/";
		} else {
			model.addAttribute("events", events.stream().map(EventDescriptor::new).collect(Collectors.toList()));
			return "/event/event-list";
		}
	}
	
	
	private static BindingResult fromModelIfPresent(Model model, String specialCode) {
		if(model.containsAttribute("error")) {
			return (BindingResult) model.asMap().get("error");
		} else {
			BeanPropertyBindingResult error = new BeanPropertyBindingResult(specialCode, "promoCode");
			model.addAttribute("error", error);
			return error;
		}
	}

	@RequestMapping(value = "/event/{eventName}", method = RequestMethod.GET)
	public String showEvent(@PathVariable("eventName") String eventName, @RequestParam(value = "promoCode", required = false) String promoCode, Model model) {

		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		
		if(!event.isPresent()) {
			return REDIRECT + "/";
		}
		
		Optional<String> maybeSpecialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode));
		Optional<SpecialPrice> specialCode = maybeSpecialCode.flatMap((trimmedCode) -> optionally(() -> specialPriceRepository.getByCode(trimmedCode)));
		
		if (maybeSpecialCode.isPresent() && !specialCode.isPresent()) {
			BindingResult errors = fromModelIfPresent(model, maybeSpecialCode.get());
			errors.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND, new Object[]{maybeSpecialCode.get()}, null);
			model.addAttribute("hasErrors", true);
			
		}
		if (specialCode.isPresent() && specialCode.get().getStatus() != Status.FREE) {
			BindingResult errors = fromModelIfPresent(model, maybeSpecialCode.get());
			errors.reject(ErrorsCode.STEP_1_CODE_USED, new Object[]{maybeSpecialCode.get()}, null);
			model.addAttribute("hasErrors", true);
		}
		
		

		Event ev = event.get();
		final ZonedDateTime now = ZonedDateTime.now(ev.getZoneId());
		final int maxTickets = configurationManager.getIntConfigValue(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, 5);
		//hide access restricted ticket categories
		List<SaleableTicketCategory> t = ticketCategoryRepository.findAllTicketCategories(ev.getId()).stream()
                .filter((c) -> !c.isAccessRestricted() || (specialCode.isPresent() && specialCode.get().getTicketCategoryId() == c.getId() && specialCode.get().getStatus() == Status.FREE))
                .map((m) -> new SaleableTicketCategory(m, now, ev, ticketRepository.countUnsoldTicket(ev.getId(), m.getId()), maxTickets))
                .collect(Collectors.toList());
		//


		LocationDescriptor ld = LocationDescriptor.fromGeoData(ev.getLatLong(), TimeZone.getTimeZone(ev.getTimeZone()),
				configurationManager.getStringConfigValue(MAPS_CLIENT_API_KEY));
        final EventDescriptor eventDescriptor = new EventDescriptor(ev);
		model.addAttribute("event", eventDescriptor)//
			.addAttribute("organizer", organizationRepository.getById(ev.getOrganizationId()))
			.addAttribute("ticketCategories", t)//
			.addAttribute("hasAccessRestrictedCategory", ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(ev.getId()).intValue() > 0)
			.addAttribute("promoCode", promoCode)
			.addAttribute("locationDescriptor", ld)
            .addAttribute("forwardButtonDisabled", t.stream().noneMatch(SaleableTicketCategory::getSaleable));
		model.asMap().putIfAbsent("hasErrors", false);//
		return "/event/show-event";
	}
}
