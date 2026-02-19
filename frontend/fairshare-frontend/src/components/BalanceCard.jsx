import React from "react";

export default function BalanceCard({user,balance}) {
  return (
    <div style={{border:"1px solid #ccc", margin:"5px", padding:"5px"}}>
      <b>User {user}</b>: {balance} {balance>=0?"owed":"owes"}
    </div>
  );
}
