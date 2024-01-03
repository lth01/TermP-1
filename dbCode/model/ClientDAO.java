package dbCode.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import dbCode.utill.CloseHelper;

public class ClientDAO {                    //DAO는 DB변경 메소드 집합
	public static Statement stmt = null;
	public static PreparedStatement pstmt = null;
	public static Connection conn = null;
	public static ResultSet rs = null;

	public static void open() throws SQLException {
		if (conn != null) {
			stmt = conn.createStatement();

		}
	}

	public static void close() {
		try {
			CloseHelper.close(rs);
			CloseHelper.close(stmt);
			CloseHelper.close(pstmt);
			CloseHelper.close(conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean signup(String id, String pwd, String uname, String tel, String email) {
		try {
			pstmt = conn.prepareStatement("INSERT INTO CLIENT VALUES( ?, ?, ?, ? ,?,sysdate,null)");
			pstmt.setString(1, id);
			pstmt.setString(2, pwd);
			pstmt.setString(3, uname);
			pstmt.setString(4, tel);
			pstmt.setString(5, email);
			int result = pstmt.executeUpdate();
			System.out.println(result);
			if (result >= 1) {
				System.out.println(result + " add Success");
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public static boolean createroom(String uid, String roomName) {
		//방 생성 진행사항
		/**
		 * 1.room table에 insert 진행
		 * 2.participants에 create요청을 한 유저의 정보 insert
		 * 1,2가 모두 정상적으로 해결되었을 경우 true 반환
		 */
		try {
			rs = stmt.executeQuery("select sysdate from dual");
			rs.next();
			String sysdate = rs.getString(1);
			pstmt = conn.prepareStatement("insert into room(id, room_name) values(seq_room.nextval, ?)");
			pstmt.setString(1, roomName);
			int q1result = pstmt.executeUpdate();
			if (q1result != 1)
				return false;
			rs = stmt.executeQuery("select seq_room.currval from room");
			rs.next();
			String roomID = rs.getString(1);
			//*participants는 id, clientID,roomId,sysdate
			pstmt = conn.prepareStatement(
				"insert into participants(id,client_id,room_id,entry) values(seq_Participants.nextval,?,?,sysdate)");
			pstmt.setString(1, uid);
			pstmt.setString(2, roomID);
			int q2result = pstmt.executeUpdate();
			if (q2result == 1) {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}

	public static StringBuffer showroom(String id) {
		StringBuffer buffer = new StringBuffer();
		/**
		 * ShowRoom은 Client id를 통해 Participants에서 얻을 수 있는 room_id와
		 * room_id의 값을 갖는 room의 name을 가져오는 쿼리를 설계한다.
		 * room_name:room_id\n 형태로 buffer에 입력한뒤 반환한다.
		 */
		try {
			System.out.println(id);
			rs = stmt.executeQuery(
				"select R.room_name, R.id from room R JOIN PARTICIPANTS p ON R.id = room_id WHERE client_id='" + id
					+ "'");
			System.out.println("id :" + id);
			while (rs.next()) {
				String rname = rs.getString(1);
				String rId = rs.getString(2);
				System.out.println(rname + " " + rId);
				if (rname.isEmpty() || rId.isEmpty()) {
					//*두 엔티티중 하나라도 오지 않았다면 쿼리의 이상 발생가능성 존재.
				} else {
					buffer.append(rname + ":" + rId + "\n");
				}
			}
		} catch (SQLException e) {
			System.out.println("inshowroom");
			e.printStackTrace();
		}
		return buffer;
	}

	public static StringBuffer enterroom(String room_id, String uid) {
		StringBuffer buffer = new StringBuffer();
		//*들어간 방의 id와 uid를 같이 전달한다. 전달된 room_id,ClientId는 필요한 메세지를 반환값으로 가져온다.
		try {
			rs = stmt.executeQuery("select c.name,m.message,m.datemessage FROM CLIENT c \n"
				+ "JOIN PARTICIPANTS p ON p.CLIENT_ID = c.ID JOIN MESSAGE m ON m.CLIENT_ID = p.CLIENT_ID \n"
				+ "and datemessage >=(select entry from participants p where p.room_id = '" + room_id
				+ "' and p.client_id ='" + uid + "' )\n"
				+ "ORDER BY m.DATEMESSAGE");
			while (rs.next()) {
				String name = rs.getString(1);
				String msg = rs.getString(2);
				String date = rs.getDate(3).toString();
				if (msg.isEmpty() || date.isEmpty()) {
					//error!
					return null;
				}
				buffer.append(name + ":" + msg + ":" + date + "\n");
			}
		} catch (SQLException e) {
			System.out.println("enteroom Error!");
		}
		return buffer;
	}

	public static StringBuffer chat(String room_id, String uid, String msg) {
		/**
		 * 1.chatting을 하기 위한 room_id와 uid를 준비한다.
		 * 2.query를 사용해 chat을 Message table에 insert한ㄷㅏ.
		 * 3.message가 정상적으로 insert되었다면, pstmt는 1>=의 값을 리턴한다.
		 * 4.pstmt가 1>=을 리턴했다면, 동일한 room_id를 갖는 참가자들의 Client Id를 모두 가져온다.
		 * 5.uid를 :구분자를 사용하여 전달한다.
		 */
		StringBuffer buffer = new StringBuffer();
		try {
			pstmt = conn.prepareStatement("insert into Message Values(seq_Message.nextval,?,?,?,sysdate)");
			//*primary key sequence 필요.
			pstmt.setString(1, uid);
			pstmt.setString(2, room_id);
			pstmt.setString(3, msg);
			int result = pstmt.executeUpdate();
			if (result >= 1) {
				//*정상적으로 처리되었다면,
				//*participants 테이블에서 list를 불러온다.
				rs = stmt.executeQuery("select p.client_id,p.room_id from participants p where p.room_id = " + room_id);
				while (rs.next()) {
					String Clientid = rs.getString(1);
					Statement stmt2 = conn.createStatement();
					ResultSet rs2 = stmt2.executeQuery(
						"select ip from Client c where c.id = " + "\'" + Clientid + "\'");
					rs2.next();
					String Clientip = rs2.getString(1);
					buffer.append(room_id + ":" + Clientip + "\n");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return buffer;
	}

	public static boolean invite(String room_id, String destId) throws SQLException {
		pstmt = conn.prepareStatement(
			"insert into participants(id,client_id,room_id,entry) VALUES (seq_Participants.nextval,?,?,sysdate)");
		pstmt.setString(1, destId);
		pstmt.setString(2, room_id);
		int result = pstmt.executeUpdate();
		if (result >= 1) {
			pstmt = conn.prepareStatement("select ip from Client where id =?");

			return true;
		}
		return false;
	}

	public void Update() {
	}

	public void Delete() {
	}

	public static boolean Login(String id, String pwd, String ip) {
		try {
			rs = stmt.executeQuery("SELECT * FROM Client WHERE id = " + "\'" + id + "\'");
			String rw = null;
			while (rs.next()) {
				rw = rs.getString(1);
				System.out.println(rs.getString(1) + " " + rs.getString(2));
				if (id.equals(rw)) { //*rw와 id가 같았다면
					if (rs.getString(2).equals(pwd)) {
						pstmt = conn.prepareStatement("update client set ip=? where id = ?");
						pstmt.setString(1, ip);
						pstmt.setString(2, id);
						if (pstmt.executeUpdate() == 1)
							return true;
						//pwd is correct?
					}
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}

	public static void setConn(Connection conn) {
		ClientDAO.conn = conn;
	}
}