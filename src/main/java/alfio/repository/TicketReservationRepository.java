/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.repository;

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.model.TicketReservation;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@QueryRepository
public interface TicketReservationRepository {

	@Query("insert into tickets_reservation(id, validity, status) values (:id, :validity, 'PENDING')")
	int createNewReservation(@Bind("id") String id, @Bind("validity") Date validity);

	@Query("update tickets_reservation set status = :status, full_name = :fullName, email_address = :email, billing_address = :billingAddress, confirmation_ts = :timestamp where id = :reservationId")
	int updateTicketReservation(@Bind("reservationId") String reservationId, @Bind("status") String status,
			@Bind("email") String email, @Bind("fullName") String fullName,
			@Bind("billingAddress") String billingAddress, @Bind("timestamp") ZonedDateTime timestamp);
	
	@Query("update tickets_reservation set status = :status where id = :reservationId")
	int updateTicketStatus(@Bind("reservationId") String reservationId, @Bind("status") String status);

	@Query("select * from tickets_reservation where id = :id")
	TicketReservation findReservationById(@Bind("id") String id);

	@Query("select id from tickets_reservation where validity < :date and status = 'PENDING'")
	List<String> findExpiredReservation(@Bind("date") Date date);

    @Query("select id from tickets_reservation where validity < :date and status = 'IN_PAYMENT'")
    List<String> findStuckReservations(@Bind("date") Date date);

	@Query("delete from tickets_reservation where id in (:ids)")
	int remove(@Bind("ids") List<String> ids);
}
